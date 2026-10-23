-- Archived historical migration; intentionally outside supabase/migrations so fresh resets do not replay duplicate policies.
-- The equivalent RLS policies are already present in 20261016114000_initial_private_messaging.sql.
-- Original filename: supabase/migrations/20261017120000_step2_messaging_rls.sql

-- Step 2 hardening: registration index and authenticated messaging RLS.
-- Additive and compatible with the existing conversations.is_group / messages.body schema.
begin;
alter table public.profiles add column if not exists created_at timestamptz not null default now();
create index if not exists idx_profiles_created_at on public.profiles (created_at desc);
alter table public.profiles enable row level security;
drop policy if exists "profiles readable by signed-in users" on public.profiles;
create policy "profiles readable by signed-in users" on public.profiles for select to authenticated using (auth.role() = 'authenticated');
alter table public.conversation_members enable row level security;
drop policy if exists "members can view membership" on public.conversation_members;
create policy "members can view membership" on public.conversation_members for select to authenticated using (user_id = auth.uid());
alter table public.messages enable row level security;
drop policy if exists "members can read messages" on public.messages;
create policy "members can read messages" on public.messages for select to authenticated using (
 exists (select 1 from public.conversation_members cm where cm.conversation_id = messages.conversation_id and cm.user_id = auth.uid())
);
drop policy if exists "members can send as themselves" on public.messages;
create policy "members can send as themselves" on public.messages for insert to authenticated with check (
 auth.uid() = sender_id and exists (
  select 1 from public.conversation_members cm where cm.conversation_id = messages.conversation_id and cm.user_id = auth.uid()
 )
);
commit;
