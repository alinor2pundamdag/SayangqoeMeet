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
END $$;

-- Verify authenticated device-token registration atomically transfers a reused
-- installation token to the currently authenticated account after account switch.
DO $
BEGIN
  IF NOT has_function_privilege('authenticated', 'public.register_device_token(text,text)', 'EXECUTE')
     OR has_function_privilege('anon', 'public.register_device_token(text,text)', 'EXECUTE') THEN
    RAISE EXCEPTION 'Device-token registration RPC grants are not least-privilege';
  END IF;
END $;

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

DO $
BEGIN
  IF (SELECT count(*) FROM public.device_tokens WHERE token = 'phase2-shared-device-token') <> 1
     OR (SELECT user_id FROM public.device_tokens WHERE token = 'phase2-shared-device-token')
        <> 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid THEN
    RAISE EXCEPTION 'Device token was not atomically reassigned to the active account';
  END IF;
END $;

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
