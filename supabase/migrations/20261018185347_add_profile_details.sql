-- Extend public.profiles with additional profile fields, and protect the
-- sensitive ones with column-level privileges instead of relying on the
-- broad row-level "readable by signed-in users" policy alone.
begin;

alter table public.profiles
    add column if not exists bio text,
    add column if not exists birthday date,
    add column if not exists religion text,
    add column if not exists address text,
    add column if not exists phone_number text;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'profiles_bio_length'
    ) then
        alter table public.profiles
            add constraint profiles_bio_length check (bio is null or char_length(bio) <= 500);
    end if;
end $$;

-- Public discovery (Registered Users list, profile lookups by other members)
-- must only ever see non-sensitive fields. Replace the blanket table grant
-- with an explicit column list so bio/birthday/religion/address/phone_number
-- cannot be read through a direct PostgREST query, by any authenticated user,
-- including the row owner (the owner reads their own values via the RPC below).
revoke select on public.profiles from authenticated;
grant select (id, username, display_name, avatar_url, created_at, updated_at)
    on public.profiles to authenticated;

-- A signed-in user reads their own full profile (including the restricted
-- columns) only through this function. It is SECURITY DEFINER so it can see
-- past the column grant above, but it hard-filters to auth.uid() internally,
-- so it can never be used to read another user's sensitive fields regardless
-- of what the caller passes in (it takes no arguments).
create or replace function public.get_my_profile()
returns setof public.profiles
language sql
security definer
set search_path = public
stable
as $$
    select * from public.profiles where id = auth.uid();
$$;

revoke all on function public.get_my_profile() from public, anon;
grant execute on function public.get_my_profile() to authenticated;

commit;
