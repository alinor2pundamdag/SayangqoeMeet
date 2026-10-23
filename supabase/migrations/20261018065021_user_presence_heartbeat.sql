-- Lightweight authenticated-user online presence heartbeat.
-- A user is shown online only when last_seen_at is within the client's 90-second window.
begin;

create table if not exists public.user_presence (
    user_id uuid primary key references auth.users(id) on delete cascade,
    last_seen_at timestamptz not null default now()
);

create index if not exists user_presence_last_seen_at_idx
    on public.user_presence (last_seen_at desc);

alter table public.user_presence enable row level security;

revoke all on public.user_presence from anon;
revoke all on public.user_presence from authenticated;
grant select, insert, update on public.user_presence to authenticated;

drop policy if exists user_presence_read_authenticated on public.user_presence;
create policy user_presence_read_authenticated
    on public.user_presence for select to authenticated
    using (true);

drop policy if exists user_presence_insert_self on public.user_presence;
create policy user_presence_insert_self
    on public.user_presence for insert to authenticated
    with check (auth.uid() = user_id);

drop policy if exists user_presence_update_self on public.user_presence;
create policy user_presence_update_self
    on public.user_presence for update to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

commit;
