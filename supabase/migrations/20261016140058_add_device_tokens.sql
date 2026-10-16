begin;

create table if not exists public.device_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    token text not null unique,
    platform text not null default 'android' check (platform in ('android')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists device_tokens_user_idx on public.device_tokens(user_id);

alter table public.device_tokens enable row level security;

drop policy if exists "users view own device tokens" on public.device_tokens;
create policy "users view own device tokens"
on public.device_tokens for select to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "users register own device tokens" on public.device_tokens;
create policy "users register own device tokens"
on public.device_tokens for insert to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "users update own device tokens" on public.device_tokens;
create policy "users update own device tokens"
on public.device_tokens for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "users remove own device tokens" on public.device_tokens;
create policy "users remove own device tokens"
on public.device_tokens for delete to authenticated
using ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.device_tokens to authenticated;

commit;
