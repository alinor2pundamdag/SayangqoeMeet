-- Run after a complete fresh migration replay against local Supabase.
-- Test data is isolated to this disposable CI database.
BEGIN;

DO $$
DECLARE
  user_a constant uuid := 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  user_b constant uuid := 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  conversation_a constant uuid := 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  conversation_b constant uuid := 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
  message_a constant uuid := 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
  message_b constant uuid := 'ffffffff-ffff-4fff-8fff-ffffffffffff';
BEGIN
  INSERT INTO auth.users (
    id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at
  ) VALUES
    (user_a, 'authenticated', 'authenticated', 'phase2-a@example.test', '', now(),
     '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()),
    (user_b, 'authenticated', 'authenticated', 'phase2-b@example.test', '', now(),
     '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now())
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, username, display_name)
  VALUES (user_a, 'phase2usera', 'Phase 2 User A'),
         (user_b, 'phase2userb', 'Phase 2 User B')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.conversations (id, created_by, is_group, title)
  VALUES (conversation_a, user_a, false, 'A private conversation'),
         (conversation_b, user_b, false, 'B private conversation')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.conversation_members (conversation_id, user_id)
  VALUES (conversation_a, user_a), (conversation_b, user_b)
  ON CONFLICT DO NOTHING;

  INSERT INTO public.messages (id, conversation_id, sender_id, body)
  VALUES (message_a, conversation_a, user_a, 'visible only to user A'),
         (message_b, conversation_b, user_b, 'visible only to user B')
  ON CONFLICT (id) DO NOTHING;
END $$;

-- Guard the grant/RLS boundaries fixed during Phase 2 as regression assertions.
DO $$
BEGIN
  IF NOT has_table_privilege('authenticated', 'public.pinned_messages', 'SELECT')
     OR NOT has_table_privilege('authenticated', 'public.pinned_messages', 'INSERT')
     OR NOT has_table_privilege('authenticated', 'public.pinned_messages', 'DELETE') THEN
    RAISE EXCEPTION 'Authenticated pinned-message SELECT/INSERT/DELETE grants are missing';
  END IF;
  IF has_table_privilege('authenticated', 'public.pinned_messages', 'UPDATE')
     OR has_table_privilege('anon', 'public.pinned_messages', 'SELECT') THEN
    RAISE EXCEPTION 'Pinned-message grants exceed the intended minimum privileges';
  END IF;
  IF has_table_privilege('authenticated', 'public.messages', 'DELETE')
     OR has_table_privilege('authenticated', 'public.profiles', 'DELETE') THEN
    RAISE EXCEPTION 'Client hard DELETE on messages/profiles must remain revoked';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'pinned_messages' AND c.relrowsecurity
  ) THEN
    RAISE EXCEPTION 'RLS must remain enabled on pinned_messages';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM storage.buckets WHERE id='profile-avatars' AND public IS TRUE
  ) THEN
    RAISE EXCEPTION 'Profile avatar bucket must exist and remain public for avatar reads';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM storage.buckets WHERE id='chat-attachments' AND public IS FALSE
  ) THEN
    RAISE EXCEPTION 'Chat attachments bucket must remain private';
  END IF;
  IF (
    SELECT count(*) FROM pg_publication_tables
    WHERE pubname='supabase_realtime' AND schemaname='public'
      AND tablename IN ('messages','call_sessions','user_presence','pinned_messages')
  ) <> 4 THEN
    RAISE EXCEPTION 'Messages, calls, presence, and pinned messages must be in supabase_realtime';
  END IF;
  IF (
    SELECT count(*) FROM pg_policies
    WHERE schemaname='storage' AND tablename='objects'
      AND policyname IN (
        'Conversation members can read chat attachments',
        'Conversation members can upload own chat attachments',
        'Conversation members can delete own chat attachments'
      )
      AND (coalesce(qual,'') || coalesce(with_check,'')) LIKE '%CASE WHEN%'
      AND (coalesce(qual,'') || coalesce(with_check,'')) LIKE '%[0-9a-f]{8}%'
  ) <> 3 THEN
    RAISE EXCEPTION 'Chat attachment storage policies must guard malformed UUID paths before casting';
  END IF;
END $$;


-- Call lifecycle regression fixtures and participant transition tests.
INSERT INTO public.call_sessions (id, caller_id, callee_id, call_type, status)
VALUES
  ('11111111-1111-4111-8111-111111111111',
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'voice', 'ringing'),
  ('22222222-2222-4222-8222-222222222222',
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'video', 'ringing')
ON CONFLICT (id) DO NOTHING;

-- Caller must not accept its own call; caller cancellation from ringing is valid.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $
BEGIN
  BEGIN
    UPDATE public.call_sessions
    SET status = 'accepted', answered_at = now()
    WHERE id = '11111111-1111-4111-8111-111111111111';
    RAISE EXCEPTION 'Caller incorrectly accepted its own call' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
END $;
UPDATE public.call_sessions
SET status = 'cancelled', ended_at = now()
WHERE id = '11111111-1111-4111-8111-111111111111';
RESET ROLE;

DO $
BEGIN
  IF (SELECT status FROM public.call_sessions
      WHERE id = '11111111-1111-4111-8111-111111111111') <> 'cancelled' THEN
    RAISE EXCEPTION 'Caller cancellation from ringing must be persisted';
  END IF;
END $;

-- Callee may accept with an answer, a participant may connect, and a terminal
-- call must not be reopened by either participant.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
UPDATE public.call_sessions
SET status = 'accepted', answer_sdp = 'test-answer-sdp', answered_at = now()
WHERE id = '22222222-2222-4222-8222-222222222222';
RESET ROLE;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
UPDATE public.call_sessions
SET status = 'connected'
WHERE id = '22222222-2222-4222-8222-222222222222';
UPDATE public.call_sessions
SET status = 'ended', ended_at = now()
WHERE id = '22222222-2222-4222-8222-222222222222';
DO $
BEGIN
  BEGIN
    UPDATE public.call_sessions
    SET status = 'connected'
    WHERE id = '22222222-2222-4222-8222-222222222222';
    RAISE EXCEPTION 'Terminal call was incorrectly reopened' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '23514' THEN
    NULL;
  END;
END $;
RESET ROLE;

-- Verify authenticated device-token registration atomically transfers a reused
-- installation token to the currently authenticated account after account switch.
DO $$
BEGIN
  IF NOT has_function_privilege('authenticated', 'public.register_device_token(text,text)', 'EXECUTE')
     OR has_function_privilege('anon', 'public.register_device_token(text,text)', 'EXECUTE') THEN
    RAISE EXCEPTION 'Device-token registration RPC grants are not least-privilege';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
      AND p.proname = 'register_device_token'
      AND p.prosecdef
      AND p.proconfig @> ARRAY['search_path=public, pg_temp']
  ) THEN
    RAISE EXCEPTION 'Device-token registration RPC must be SECURITY DEFINER with a hardened search_path';
  END IF;
END $$;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
SELECT public.register_device_token('phase2-shared-device-token', 'android');
RESET ROLE;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
SELECT public.register_device_token('phase2-shared-device-token', 'android');
RESET ROLE;

DO $$
BEGIN
  IF (SELECT count(*) FROM public.device_tokens WHERE token = 'phase2-shared-device-token') <> 1
     OR (SELECT user_id FROM public.device_tokens WHERE token = 'phase2-shared-device-token')
        <> 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid THEN
    RAISE EXCEPTION 'Device token was not atomically reassigned to the active account';
  END IF;
END $$;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);

DO $$
DECLARE visible_count integer;
BEGIN
  SELECT count(*) INTO visible_count
  FROM public.messages
  WHERE id IN (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    'ffffffff-ffff-4fff-8fff-ffffffffffff'
  );
  IF visible_count <> 1 THEN
    RAISE EXCEPTION 'RLS isolation failed for user A: expected 1 visible message, got %', visible_count;
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.messages
    WHERE id = 'ffffffff-ffff-4fff-8fff-ffffffffffff'
  ) THEN
    RAISE EXCEPTION 'RLS isolation failed: user A can read user B message';
  END IF;
END $$;

RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);

DO $$
DECLARE visible_count integer;
BEGIN
  SELECT count(*) INTO visible_count
  FROM public.messages
  WHERE id IN (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    'ffffffff-ffff-4fff-8fff-ffffffffffff'
  );
  IF visible_count <> 1 THEN
    RAISE EXCEPTION 'RLS isolation failed for user B: expected 1 visible message, got %', visible_count;
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.messages
    WHERE id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
  ) THEN
    RAISE EXCEPTION 'RLS isolation failed: user B can read user A message';
  END IF;
END $$;

ROLLBACK;
