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

  UPDATE public.messages
  SET attachment_path = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/phase4-fixture.png',
      attachment_mime_type = 'image/png',
      attachment_name = 'phase4-fixture.png'
  WHERE id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';

  INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
  VALUES (
    'chat-attachments',
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/phase4-fixture.png',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    '{"mimetype":"image/png","size":1}'::jsonb
  )
  ON CONFLICT (bucket_id, name) DO NOTHING;

  INSERT INTO public.conversation_invites
    (id, conversation_id, inviter_id, recipient_id, status)
  VALUES
    ('12111111-1111-4111-8111-111111111111', conversation_a, user_a, user_b, 'pending')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.pinned_messages
    (id, conversation_id, message_id, pinned_by)
  VALUES
    ('13111111-1111-4111-8111-111111111111', conversation_a, message_a, user_a),
    ('13222222-2222-4222-8222-222222222222', conversation_b, message_b, user_b)
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

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname='storage' AND tablename='objects'
      AND policyname='Conversation members can read chat attachments'
      AND lower(coalesce(qual,'')) LIKE '%attachment_path%'
      AND lower(coalesce(qual,'')) LIKE '%attachment_purged_at is null%'
      AND lower(coalesce(qual,'')) LIKE '%deleted_at is null%'
      AND lower(coalesce(qual,'')) LIKE '%conversation_members%'
  ) THEN
    RAISE EXCEPTION 'Private attachment reads must require a live, unpurged message attachment and conversation membership';
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

  IF has_column_privilege('authenticated', 'public.messages', 'id', 'INSERT')
     OR has_column_privilege('authenticated', 'public.messages', 'created_at', 'INSERT')
     OR has_column_privilege('authenticated', 'public.messages', 'deleted_at', 'INSERT')
     OR has_column_privilege('authenticated', 'public.messages', 'edited_at', 'INSERT')
     OR has_column_privilege('authenticated', 'public.messages', 'attachment_purged_at', 'INSERT') THEN
    RAISE EXCEPTION 'Authenticated clients must not forge message identity or server-owned state on INSERT';
  END IF;
  IF NOT has_column_privilege('authenticated', 'public.messages', 'conversation_id', 'INSERT')
     OR NOT has_column_privilege('authenticated', 'public.messages', 'sender_id', 'INSERT')
     OR NOT has_column_privilege('authenticated', 'public.messages', 'body', 'INSERT')
     OR NOT has_column_privilege('authenticated', 'public.messages', 'attachment_path', 'INSERT')
     OR NOT has_column_privilege('authenticated', 'public.messages', 'attachment_mime_type', 'INSERT')
     OR NOT has_column_privilege('authenticated', 'public.messages', 'attachment_name', 'INSERT') THEN
    RAISE EXCEPTION 'Supported text/attachment message INSERT columns must remain writable';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'messages'
      AND t.tgname = 'messages_insert_integrity_guard' AND NOT t.tgisinternal
  ) THEN
    RAISE EXCEPTION 'Message INSERT integrity guard trigger is missing';
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


-- Authenticated clients must not forge soft-delete or cleanup state when inserting.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
BEGIN
  BEGIN
    INSERT INTO public.messages (conversation_id, sender_id, body, attachment_purged_at)
    VALUES ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'forged cleanup state', now());
    RAISE EXCEPTION 'Authenticated client incorrectly inserted server-owned cleanup state' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
  BEGIN
    INSERT INTO public.messages (conversation_id, sender_id, body, deleted_at)
    VALUES ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'pre-deleted message', now());
    RAISE EXCEPTION 'Authenticated client incorrectly inserted a pre-deleted message' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
END $$;
RESET ROLE;

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


-- Verify the two intentionally exposed SECURITY DEFINER RPCs remain caller-scoped.
-- They must be executable only by authenticated callers and must never expose another user's rows.
DO $$
BEGIN
  IF NOT has_function_privilege('authenticated', 'public.get_my_profile()', 'EXECUTE')
     OR has_function_privilege('anon', 'public.get_my_profile()', 'EXECUTE')
     OR NOT has_function_privilege('authenticated', 'public.get_my_conversation_list()', 'EXECUTE')
     OR has_function_privilege('anon', 'public.get_my_conversation_list()', 'EXECUTE') THEN
    RAISE EXCEPTION 'Profile/conversation RPC grants exceed or fall short of the intended authenticated-only contract';
  END IF;

  IF (
    SELECT count(*) FROM pg_proc p
    JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public'
      AND p.proname IN ('get_my_profile','get_my_conversation_list')
      AND p.prosecdef
      AND p.proconfig @> ARRAY['search_path=public, pg_temp']
  ) <> 2 THEN
    RAISE EXCEPTION 'Profile/conversation SECURITY DEFINER RPCs must pin search_path';
  END IF;
END $$;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE profile_count integer; conversation_count integer;
BEGIN
  SELECT count(*) INTO profile_count FROM public.get_my_profile();
  IF profile_count <> 1 OR EXISTS (
    SELECT 1 FROM public.get_my_profile()
    WHERE id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid
  ) THEN
    RAISE EXCEPTION 'get_my_profile exposed rows outside user A or failed to return user A profile';
  END IF;

  SELECT count(*) INTO conversation_count FROM public.get_my_conversation_list();
  IF conversation_count <> 1 OR EXISTS (
    SELECT 1 FROM public.get_my_conversation_list()
    WHERE conversation_id <> 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'::uuid
  ) THEN
    RAISE EXCEPTION 'get_my_conversation_list exposed rows outside user A membership';
  END IF;
END $$;
RESET ROLE;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE profile_count integer; conversation_count integer;
BEGIN
  SELECT count(*) INTO profile_count FROM public.get_my_profile();
  IF profile_count <> 1 OR EXISTS (
    SELECT 1 FROM public.get_my_profile()
    WHERE id <> 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid
  ) THEN
    RAISE EXCEPTION 'get_my_profile exposed rows outside user B or failed to return user B profile';
  END IF;

  SELECT count(*) INTO conversation_count FROM public.get_my_conversation_list();
  IF conversation_count <> 1 OR EXISTS (
    SELECT 1 FROM public.get_my_conversation_list()
    WHERE conversation_id <> 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'::uuid
  ) THEN
    RAISE EXCEPTION 'get_my_conversation_list exposed rows outside user B membership';
  END IF;
END $$;
RESET ROLE;

 
-- Invitation and pinned-message isolation must match the two-user membership boundary.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE invite_count integer; pin_count integer;
BEGIN
  SELECT count(*) INTO invite_count FROM public.conversation_invites
  WHERE id='12111111-1111-4111-8111-111111111111';
  IF invite_count <> 1 THEN
    RAISE EXCEPTION 'Invite participant A must be able to read its invitation';
  END IF;
  SELECT count(*) INTO pin_count FROM public.pinned_messages
  WHERE id IN ('13111111-1111-4111-8111-111111111111',
               '13222222-2222-4222-8222-222222222222');
  IF pin_count <> 1 OR NOT EXISTS (
    SELECT 1 FROM public.pinned_messages
    WHERE id='13111111-1111-4111-8111-111111111111'
  ) THEN
    RAISE EXCEPTION 'User A must only see pinned messages for conversation A';
  END IF;
  BEGIN
    INSERT INTO public.pinned_messages (conversation_id, message_id, pinned_by)
    VALUES ('dddddddd-dddd-4ddd-8ddd-dddddddddddd',
            'ffffffff-ffff-4fff-8fff-ffffffffffff',
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
    RAISE EXCEPTION 'User A incorrectly pinned a message in conversation B' USING ERRCODE = 'P0001';
  EXCEPTION WHEN SQLSTATE '42501' THEN
    NULL;
  END;
  DELETE FROM public.pinned_messages
  WHERE id='13222222-2222-4222-8222-222222222222';
END $$;
RESET ROLE;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE invite_count integer; pin_count integer;
BEGIN
  SELECT count(*) INTO invite_count FROM public.conversation_invites
  WHERE id='12111111-1111-4111-8111-111111111111';
  IF invite_count <> 1 THEN
    RAISE EXCEPTION 'Invite recipient B must be able to read its invitation';
  END IF;
  SELECT count(*) INTO pin_count FROM public.pinned_messages
  WHERE id IN ('13111111-1111-4111-8111-111111111111',
               '13222222-2222-4222-8222-222222222222');
  IF pin_count <> 1 OR NOT EXISTS (
    SELECT 1 FROM public.pinned_messages
    WHERE id='13222222-2222-4222-8222-222222222222'
  ) THEN
    RAISE EXCEPTION 'User B must only see pinned messages for conversation B';
  END IF;
END $$;
RESET ROLE;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', '77777777-7777-4777-8777-777777777777', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE invite_count integer; pin_count integer;
BEGIN
  SELECT count(*) INTO invite_count FROM public.conversation_invites
  WHERE id='12111111-1111-4111-8111-111111111111';
  SELECT count(*) INTO pin_count FROM public.pinned_messages
  WHERE id IN ('13111111-1111-4111-8111-111111111111',
               '13222222-2222-4222-8222-222222222222');
  IF invite_count <> 0 OR pin_count <> 0 THEN
    RAISE EXCEPTION 'Non-participant C can read invitations or pinned messages';
  END IF;
END $$;
RESET ROLE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.pinned_messages
    WHERE id='13222222-2222-4222-8222-222222222222'
  ) THEN
    RAISE EXCEPTION 'User A unexpectedly deleted user B pinned message';
  END IF;
END $$;


-- Idempotency key regression: retries for one sender/key must create one row only.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema='public' AND table_name='messages' AND column_name='client_request_id'
      AND data_type='uuid'
  ) THEN
    RAISE EXCEPTION 'messages.client_request_id UUID column is missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname='public' AND tablename='messages'
      AND indexname='messages_sender_client_request_uidx'
      AND indexdef LIKE 'CREATE UNIQUE INDEX%'
  ) THEN
    RAISE EXCEPTION 'Unique sender/client_request_id index is missing';
  END IF;
  IF NOT has_column_privilege('authenticated', 'public.messages', 'client_request_id', 'INSERT') THEN
    RAISE EXCEPTION 'Authenticated clients must be able to submit a client request idempotency key';
  END IF;
END $$;

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
INSERT INTO public.messages (conversation_id, sender_id, body, client_request_id)
VALUES ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'idempotency test message',
        'abababab-abab-4bab-8bab-abababababab')
ON CONFLICT (sender_id, client_request_id) DO NOTHING;
INSERT INTO public.messages (conversation_id, sender_id, body, client_request_id)
VALUES ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'duplicate retry must not create a second row',
        'abababab-abab-4bab-8bab-abababababab')
ON CONFLICT (sender_id, client_request_id) DO NOTHING;
DO $$
BEGIN
  IF (
    SELECT count(*) FROM public.messages
    WHERE sender_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid
      AND client_request_id='abababab-abab-4bab-8bab-abababababab'::uuid
  ) <> 1 THEN
    RAISE EXCEPTION 'Message idempotency key allowed duplicate rows';
  END IF;
END $$;
RESET ROLE;

-- Admin role/audit tables are service-only; clients cannot self-assign admin or alter audit history.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relname='admin_memberships' AND c.relrowsecurity
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relname='admin_audit_log' AND c.relrowsecurity
  ) THEN
    RAISE EXCEPTION 'Admin membership and audit tables must have RLS enabled';
  END IF;

  IF has_table_privilege('anon', 'public.admin_memberships', 'SELECT')
     OR has_table_privilege('authenticated', 'public.admin_memberships', 'SELECT')
     OR has_table_privilege('anon', 'public.admin_memberships', 'INSERT')
     OR has_table_privilege('authenticated', 'public.admin_memberships', 'INSERT')
     OR has_table_privilege('anon', 'public.admin_audit_log', 'SELECT')
     OR has_table_privilege('authenticated', 'public.admin_audit_log', 'SELECT')
     OR has_table_privilege('authenticated', 'public.admin_audit_log', 'INSERT')
     OR has_table_privilege('authenticated', 'public.admin_audit_log', 'UPDATE')
     OR has_table_privilege('authenticated', 'public.admin_audit_log', 'DELETE') THEN
    RAISE EXCEPTION 'Admin membership/audit tables must not be directly accessible to client roles';
  END IF;

  IF NOT has_table_privilege('service_role', 'public.admin_memberships', 'SELECT')
     OR has_table_privilege('service_role', 'public.admin_memberships', 'UPDATE')
     OR has_table_privilege('service_role', 'public.admin_memberships', 'DELETE')
     OR NOT has_table_privilege('service_role', 'public.admin_audit_log', 'SELECT')
     OR NOT has_table_privilege('service_role', 'public.admin_audit_log', 'INSERT')
     OR has_table_privilege('service_role', 'public.admin_audit_log', 'UPDATE')
     OR has_table_privilege('service_role', 'public.admin_audit_log', 'DELETE') THEN
    RAISE EXCEPTION 'Admin service-role grants must remain least-privilege and audit append-only';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname='public' AND tablename='admin_memberships'
      AND policyname='admin_memberships_deny_clients' AND cmd='ALL'
      AND qual='false' AND with_check='false'
      AND roles @> ARRAY['anon','authenticated']::name[]
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname='public' AND tablename='admin_audit_log'
      AND policyname='admin_audit_log_deny_clients' AND cmd='ALL'
      AND qual='false' AND with_check='false'
      AND roles @> ARRAY['anon','authenticated']::name[]
  ) THEN
    RAISE EXCEPTION 'Explicit client-deny RLS policies are required for admin tables';
  END IF;
  IF has_sequence_privilege('anon', 'public.admin_audit_log_id_seq', 'USAGE')
     OR has_sequence_privilege('authenticated', 'public.admin_audit_log_id_seq', 'USAGE')
     OR NOT has_sequence_privilege('service_role', 'public.admin_audit_log_id_seq', 'USAGE') THEN
    RAISE EXCEPTION 'Admin audit identity sequence must be service-role only';
  END IF;
END $$;

-- Generic regression gates for the whole exposed public schema. These checks
-- prevent new tables/functions/FKs from silently bypassing Phase 2 hardening.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_class c
    JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public'
      AND c.relkind IN ('r','p')
      AND (
        NOT c.relrowsecurity
        OR NOT EXISTS (
          SELECT 1 FROM pg_policies p
          WHERE p.schemaname=n.nspname AND p.tablename=c.relname
        )
      )
  ) THEN
    RAISE EXCEPTION 'Every exposed public table must keep RLS enabled and at least one explicit policy';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_constraint fk
    WHERE fk.contype='f'
      AND fk.connamespace='public'::regnamespace
      AND NOT EXISTS (
        SELECT 1 FROM pg_index i
        WHERE i.indrelid=fk.conrelid
          AND i.indisvalid AND i.indisready
          AND (
            SELECT array_agg(i.indkey[k]::smallint ORDER BY k)
            FROM generate_series(0,cardinality(fk.conkey)-1) AS k
          ) = fk.conkey
      )
  ) THEN
    RAISE EXCEPTION 'Every public foreign key must have a valid index whose leading columns cover the FK columns in order';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public'
      AND p.prosecdef
      AND (
        p.proconfig IS NULL
        OR NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) cfg WHERE cfg LIKE 'search_path=%')
        OR has_function_privilege('anon',p.oid,'EXECUTE')
      )
  ) THEN
    RAISE EXCEPTION 'Every public SECURITY DEFINER function must pin search_path and deny anon EXECUTE';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_index i
    JOIN pg_class idx ON idx.oid=i.indexrelid
    JOIN pg_class tbl ON tbl.oid=i.indrelid
    JOIN pg_namespace n ON n.oid=tbl.relnamespace
    WHERE n.nspname='public'
      AND tbl.relname='admin_memberships'
      AND idx.relname='admin_memberships_granted_by_idx'
      AND i.indisvalid AND i.indisready
      AND (i.indkey::smallint[])[0]=(
        SELECT attnum::smallint FROM pg_attribute
        WHERE attrelid='public.admin_memberships'::regclass AND attname='granted_by'
      )
  ) THEN
    RAISE EXCEPTION 'The admin_memberships.granted_by FK covering index is missing or invalid';
  END IF;
END $$;

-- Actual Storage RLS behavior: only a live message attachment in a member's
-- conversation is readable. A known object path is not sufficient by itself.
RESET ROLE;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE visible_count integer;
BEGIN
  SELECT count(*) INTO visible_count
  FROM storage.objects
  WHERE bucket_id='chat-attachments'
    AND name='cccccccc-cccc-4ccc-8ccc-cccccccccccc/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/phase4-fixture.png';
  IF visible_count <> 1 THEN
    RAISE EXCEPTION 'Conversation member could not read an active message attachment';
  END IF;
END $$;

SELECT set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true);
DO $$
DECLARE visible_count integer;
BEGIN
  SELECT count(*) INTO visible_count
  FROM storage.objects
  WHERE bucket_id='chat-attachments'
    AND name='cccccccc-cccc-4ccc-8ccc-cccccccccccc/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/phase4-fixture.png';
  IF visible_count <> 0 THEN
    RAISE EXCEPTION 'Non-member could read another conversation attachment';
  END IF;
END $$;
RESET ROLE;

-- A soft-deleted message must immediately stop authorizing object reads even
-- before the scheduled cleanup worker purges the bytes.
UPDATE public.messages
SET deleted_at=now()
WHERE id='eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', true);
SELECT set_config('request.jwt.claim.role', 'authenticated', true);
DO $$
DECLARE visible_count integer;
BEGIN
  SELECT count(*) INTO visible_count
  FROM storage.objects
  WHERE bucket_id='chat-attachments'
    AND name='cccccccc-cccc-4ccc-8ccc-cccccccccccc/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/phase4-fixture.png';
  IF visible_count <> 0 THEN
    RAISE EXCEPTION 'Soft-deleted message still authorized private attachment reads';
  END IF;
END $$;
RESET ROLE;

ROLLBACK;
