-- Run after a complete fresh migration replay against local Supabase.
-- Test data is isolated to this disposable CI database.
BEGIN;

DO $$
DECLARE
  user_a constant uuid := 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  user_b constant uuid := 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  user_c constant uuid := '77777777-7777-4777-8777-777777777777';
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
     '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now()),
    (user_c, 'authenticated', 'authenticated', 'phase2-c@example.test', '', now(),
     '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, now(), now())
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, username, display_name)
  VALUES (user_a, 'phase2usera', 'Phase 2 User A'),
         (user_b, 'phase2userb', 'Phase 2 User B'),
         (user_c, 'phase2userc', 'Phase 2 User C')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.conversations (id, created_by, is_group, title)
  VALUES (conversation_a, user_a, false, 'A private conversation'),
         (conversation_b, user_b, false, 'B private conversation')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.conversation_members (conversation_id, user_id)
  VALUES (conversation_a, user_a), (conversation_b, user_b)
  ON CONFLICT DO NOTHING;

  INSERT INTO public.messages (id, conversation_id, sender_id, body, deleted_at)
  VALUES (message_a, conversation_a, user_a, 'visible only to user A', NULL),
         (message_b, conversation_b, user_b, 'visible only to user B', NULL),
         ('99999999-9999-4999-8999-999999999999', conversation_a, user_a,
          'soft-deleted and must never be selected', now())
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
      AND regexp_replace(upper(coalesce(qual,'') || coalesce(with_check,'')), '[[:space:]]+', ' ', 'g') LIKE '%CASE WHEN%'
      AND (coalesce(qual,'') || coalesce(with_check,'')) LIKE '%[0-9a-f]{8}%'
  ) <> 3 THEN
    RAISE EXCEPTION 'Chat attachment storage policies must guard malformed UUID paths before casting';
  END IF;
END $$;

-- No exposed public table may be left without RLS. Service-only media tables
-- deliberately have no client grants or client policies.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity
  ) THEN
    RAISE EXCEPTION 'Every public table must have RLS enabled';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM (VALUES
      ('public.message_media_cleanup_queue'::text),
      ('public.message_media_download_confirmations'::text)
    ) AS service_tables(table_name)
    WHERE has_table_privilege('anon', service_tables.table_name, 'SELECT')
       OR has_table_privilege('anon', service_tables.table_name, 'INSERT')
       OR has_table_privilege('anon', service_tables.table_name, 'UPDATE')
       OR has_table_privilege('anon', service_tables.table_name, 'DELETE')
       OR has_table_privilege('authenticated', service_tables.table_name, 'SELECT')
       OR has_table_privilege('authenticated', service_tables.table_name, 'INSERT')
       OR has_table_privilege('authenticated', service_tables.table_name, 'UPDATE')
       OR has_table_privilege('authenticated', service_tables.table_name, 'DELETE')
  ) THEN
    RAISE EXCEPTION 'Service-only media tables must not be accessible to anon/authenticated';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'message_media_cleanup_queue'
      AND policyname = 'message_media_cleanup_queue_deny_clients'
      AND cmd = 'ALL' AND qual = 'false' AND with_check = 'false'
      AND roles @> ARRAY['anon', 'authenticated']::name[]
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'message_media_download_confirmations'
      AND policyname = 'message_media_download_confirmations_deny_clients'
      AND cmd = 'ALL' AND qual = 'false' AND with_check = 'false'
      AND roles @> ARRAY['anon', 'authenticated']::name[]
  ) THEN
    RAISE EXCEPTION 'Explicit deny policies must protect service-only media tables';
  END IF;

  IF has_column_privilege('authenticated', 'public.profiles', 'address', 'SELECT')
     OR has_column_privilege('authenticated', 'public.profiles', 'bio', 'SELECT')
     OR has_column_privilege('authenticated', 'public.profiles', 'birthday', 'SELECT')
     OR has_column_privilege('authenticated', 'public.profiles', 'religion', 'SELECT')
     OR has_column_privilege('authenticated', 'public.profiles', 'phone_number', 'SELECT') THEN
    RAISE EXCEPTION 'Sensitive profile columns must remain inaccessible to ordinary authenticated clients';
  END IF;

  IF has_column_privilege('authenticated', 'public.messages', 'id', 'UPDATE')
     OR has_column_privilege('authenticated', 'public.messages', 'conversation_id', 'UPDATE')
     OR has_column_privilege('authenticated', 'public.messages', 'sender_id', 'UPDATE')
     OR has_column_privilege('authenticated', 'public.messages', 'created_at', 'UPDATE')
     OR has_column_privilege('authenticated', 'public.messages', 'attachment_purged_at', 'UPDATE') THEN
    RAISE EXCEPTION 'Authenticated clients must not update message identity or server-owned cleanup fields';
  END IF;
  IF NOT has_column_privilege('authenticated', 'public.messages', 'body', 'UPDATE')
     OR NOT has_column_privilege('authenticated', 'public.messages', 'deleted_at', 'UPDATE') THEN
    RAISE EXCEPTION 'Supported message edit and soft-delete columns must remain writable';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'messages'
      AND t.tgname = 'messages_update_integrity_guard' AND NOT t.tgisinternal
  ) THEN
    RAISE EXCEPTION 'Message identity/soft-delete guard trigger is missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'call_sessions' AND cmd = 'UPDATE'
      AND qual IS NOT NULL AND with_check IS NOT NULL
      AND qual LIKE '%caller_id%' AND with_check LIKE '%callee_id%'
  ) THEN
    RAISE EXCEPTION 'Call participant UPDATE policy must enforce both USING and WITH CHECK';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'call_ice_candidates'
      AND policyname = 'call_ice_participant_insert' AND cmd = 'INSERT'
      AND with_check LIKE '%ringing%' AND with_check LIKE '%connected%'
  ) THEN
    RAISE EXCEPTION 'ICE candidate inserts must be limited to active call states';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'call_sessions'
      AND policyname = 'call_sessions_caller_insert' AND cmd = 'INSERT'
      AND with_check LIKE '%ringing%'
      AND with_check LIKE '%answer_sdp IS NULL%'
      AND with_check LIKE '%answered_at IS NULL%'
      AND with_check LIKE '%ended_at IS NULL%'
  ) THEN
    RAISE EXCEPTION 'Call INSERT policy must permit only a clean initial ringing state';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'call_sessions'
      AND t.tgname = 'call_sessions_initial_state_guard' AND NOT t.tgisinternal
  ) THEN
    RAISE EXCEPTION 'Call initial-state INSERT guard trigger is missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'guard_call_session_insert'
      AND p.proconfig @> ARRAY['search_path=public, pg_temp']
  ) OR has_function_privilege('anon', 'public.guard_call_session_insert()', 'EXECUTE')
     OR has_function_privilege('authenticated', 'public.guard_call_session_insert()', 'EXECUTE') THEN
    RAISE EXCEPTION 'Call initial-state trigger function must have hardened search_path and no client EXECUTE grants';
  END IF;
END $$;

-- Database triggers must prevent moving a message to a different conversation
-- even when the caller bypasses ordinary PostgREST/RLS checks.
DO $$
BEGIN
  BEGIN
    UPDATE public.messages
    SET conversation_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
    WHERE id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
    RAISE EXCEPTION 'Message was incorrectly moved across conversations' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;

  BEGIN
    UPDATE public.messages
    SET deleted_at = NULL
    WHERE id = '99999999-9999-4999-8999-999999999999';
    RAISE EXCEPTION 'Soft-deleted message was incorrectly restored' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
END $$;


-- Call lifecycle regression fixtures and participant transition tests.
INSERT INTO public.call_sessions (id, caller_id, callee_id, call_type, status)
VALUES
  ('11111111-1111-4111-8111-111111111111',
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'voice', 'ringing'),
  ('22222222-2222-4222-8222-222222222222',
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'video', 'ringing'),
  ('33333333-3333-4333-8333-333333333333',
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'voice', 'ringing'),
  ('44444444-4444-4444-8444-444444444444',
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'voice', 'ringing')
ON CONFLICT (id) DO NOTHING;

-- Caller must not accept its own call; caller cancellation from ringing is valid.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);

-- Legitimate client call creation starts in ringing state.
INSERT INTO public.call_sessions (id, caller_id, callee_id, call_type, status)
VALUES ('55555555-5555-4555-8555-555555555555',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'voice', 'ringing');

-- Direct authenticated INSERT must not bypass lifecycle by forging a later state.
DO $$
BEGIN
  BEGIN
    INSERT INTO public.call_sessions (id, caller_id, callee_id, call_type, status, answered_at)
    VALUES ('66666666-6666-4666-8666-666666666666',
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
            'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
            'voice', 'connected', now());
    RAISE EXCEPTION 'Authenticated caller incorrectly inserted a connected call' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
END $$;

DO $$
BEGIN
  BEGIN
    UPDATE public.call_sessions
    SET status = 'accepted', answered_at = now()
    WHERE id = '11111111-1111-4111-8111-111111111111';
    RAISE EXCEPTION 'Caller incorrectly accepted its own call' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
END $$;
UPDATE public.call_sessions
SET status = 'cancelled', ended_at = now()
WHERE id = '11111111-1111-4111-8111-111111111111';
RESET ROLE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.call_sessions
    WHERE id = '55555555-5555-4555-8555-555555555555'
      AND status = 'ringing'
      AND answer_sdp IS NULL AND answered_at IS NULL AND ended_at IS NULL
  ) THEN
    RAISE EXCEPTION 'Legitimate client-created call must start ringing';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.call_sessions
    WHERE id = '66666666-6666-4666-8666-666666666666'
  ) THEN
    RAISE EXCEPTION 'Forged initial call state unexpectedly persisted';
  END IF;
  IF (SELECT status FROM public.call_sessions
      WHERE id = '11111111-1111-4111-8111-111111111111') <> 'cancelled' THEN
    RAISE EXCEPTION 'Caller cancellation from ringing must be persisted';
  END IF;
END $$;

-- Callee may decline a ringing call.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
UPDATE public.call_sessions
SET status = 'declined', ended_at = now()
WHERE id = '44444444-4444-4444-8444-444444444444';
RESET ROLE;
DO $$
BEGIN
  IF (SELECT status FROM public.call_sessions
      WHERE id = '44444444-4444-4444-8444-444444444444') <> 'declined' THEN
    RAISE EXCEPTION 'Callee decline from ringing must be persisted';
  END IF;
END $$;

-- Callee cannot mark a call accepted without publishing an SDP answer.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
BEGIN
  BEGIN
    UPDATE public.call_sessions
    SET status = 'accepted', answered_at = now()
    WHERE id = '33333333-3333-4333-8333-333333333333';
    RAISE EXCEPTION 'Callee incorrectly accepted without an SDP answer' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
END $$;
RESET ROLE;

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
DO $$
BEGIN
  BEGIN
    UPDATE public.call_sessions
    SET status = 'connected'
    WHERE id = '22222222-2222-4222-8222-222222222222';
    RAISE EXCEPTION 'Terminal call was incorrectly reopened' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '23514' THEN
    NULL;
  END;
END $$;
RESET ROLE;

-- A third authenticated user who is not a call participant cannot read messages
-- or mutate a call even if the UUID is known.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', '77777777-7777-4777-8777-777777777777', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE visible_count integer;
BEGIN
  SELECT count(*) INTO visible_count
  FROM public.messages
  WHERE id IN (
    'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    'ffffffff-ffff-4fff-8fff-ffffffffffff',
    '99999999-9999-4999-8999-999999999999'
  );
  IF visible_count <> 0 THEN
    RAISE EXCEPTION 'Unauthorized third party can read conversation messages';
  END IF;
END $$;
UPDATE public.call_sessions
SET status = 'cancelled'
WHERE id = '33333333-3333-4333-8333-333333333333';
RESET ROLE;

DO $$
BEGIN
  IF (SELECT status FROM public.call_sessions
      WHERE id = '33333333-3333-4333-8333-333333333333') <> 'ringing' THEN
    RAISE EXCEPTION 'Unauthorized third party changed a call lifecycle state';
  END IF;
END $$;

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

  IF has_table_privilege('authenticated', 'public.device_tokens', 'INSERT')
     OR has_table_privilege('authenticated', 'public.device_tokens', 'UPDATE')
     OR has_table_privilege('anon', 'public.device_tokens', 'INSERT')
     OR has_table_privilege('anon', 'public.device_tokens', 'UPDATE') THEN
    RAISE EXCEPTION 'Direct device-token INSERT/UPDATE must be denied; clients must use the authenticated RPC';
  END IF;
  IF NOT has_table_privilege('authenticated', 'public.device_tokens', 'DELETE')
     OR NOT has_table_privilege('authenticated', 'public.device_tokens', 'SELECT') THEN
    RAISE EXCEPTION 'Authenticated device-token cleanup/read privileges are missing';
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
    'ffffffff-ffff-4fff-8fff-ffffffffffff',
    '99999999-9999-4999-8999-999999999999'
  );
  IF visible_count <> 1 THEN
    RAISE EXCEPTION 'RLS/soft-delete isolation failed for user A: expected 1 visible message, got %', visible_count;
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.messages
    WHERE id IN (
      'ffffffff-ffff-4fff-8fff-ffffffffffff',
      '99999999-9999-4999-8999-999999999999'
    )
  ) THEN
    RAISE EXCEPTION 'RLS/soft-delete isolation failed: user A can read another conversation or soft-deleted message';
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
    'ffffffff-ffff-4fff-8fff-ffffffffffff',
    '99999999-9999-4999-8999-999999999999'
  );
  IF visible_count <> 1 THEN
    RAISE EXCEPTION 'RLS/soft-delete isolation failed for user B: expected 1 visible message, got %', visible_count;
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.messages
    WHERE id IN (
      'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
      '99999999-9999-4999-8999-999999999999'
    )
  ) THEN
    RAISE EXCEPTION 'RLS/soft-delete isolation failed: user B can read another conversation or soft-deleted message';
  END IF;
END $$;

ROLLBACK;
