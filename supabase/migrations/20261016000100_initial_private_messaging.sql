-- SayangqoeMeet initial secure data foundation.
-- Apply to the intended Supabase project only after confirming the project reference.
begin;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text unique,
    display_name text not null default '',
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint profiles_username_length check (username is null or char_length(username) between 3 and 24)
);

create table if not exists public.conversations (
    id uuid primary key default gen_random_uuid(),
    is_group boolean not null default false,
    title text,
    created_by uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now()
);

create table if not exists public.conversation_members (
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    joined_at timestamptz not null default now(),
    last_read_at timestamptz,
    primary key (conversation_id, user_id)
);

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    body text not null,
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    deleted_at timestamptz,
    constraint messages_body_length check (char_length(body) between 1 and 10000)
);

create index if not exists conversation_members_user_idx on public.conversation_members(user_id, conversation_id);
create index if not exists messages_conversation_created_idx on public.messages(conversation_id, created_at desc);

alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;

drop policy if exists "profiles readable by signed-in users" on public.profiles;
create policy "profiles readable by signed-in users"
on public.profiles for select to authenticated using (true);

drop policy if exists "users create own profile" on public.profiles;
create policy "users create own profile"
on public.profiles for insert to authenticated with check ((select auth.uid()) = id);

drop policy if exists "users update own profile" on public.profiles;
create policy "users update own profile"
on public.profiles for update to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

drop policy if exists "members can view conversations" on public.conversations;
create policy "members can view conversations"
on public.conversations for select to authenticated
using (
    created_by = (select auth.uid())
    or exists (
        select 1 from public.conversation_members cm
        where cm.conversation_id = id and cm.user_id = (select auth.uid())
    )
);

drop policy if exists "signed-in users create conversations" on public.conversations;
create policy "signed-in users create conversations"
on public.conversations for insert to authenticated
with check ((select auth.uid()) = created_by);

drop policy if exists "members can view membership" on public.conversation_members;
create policy "members can view membership"
on public.conversation_members for select to authenticated
using (user_id = (select auth.uid()));

-- A user may only add themselves. Trusted server-side workflows must add other members.
drop policy if exists "users join conversations as themselves" on public.conversation_members;
create policy "users join conversations as themselves"
on public.conversation_members for insert to authenticated
with check (
    (select auth.uid()) = user_id
    and exists (
        select 1 from public.conversations c
        where c.id = conversation_id and c.created_by = (select auth.uid())
    )
);

drop policy if exists "members can read messages" on public.messages;
create policy "members can read messages"
on public.messages for select to authenticated
using (exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = messages.conversation_id and cm.user_id = (select auth.uid())
));

drop policy if exists "members can send as themselves" on public.messages;
create policy "members can send as themselves"
on public.messages for insert to authenticated
with check (
    (select auth.uid()) = sender_id
    and exists (
        select 1 from public.conversation_members cm
        where cm.conversation_id = messages.conversation_id and cm.user_id = (select auth.uid())
    )
);

drop policy if exists "senders can edit own messages" on public.messages;
create policy "senders can edit own messages"
on public.messages for update to authenticated
using ((select auth.uid()) = sender_id)
with check (
    (select auth.uid()) = sender_id
    and exists (
        select 1 from public.conversation_members cm
        where cm.conversation_id = messages.conversation_id
          and cm.user_id = (select auth.uid())
    )
);

drop policy if exists "senders can delete own messages" on public.messages;
create policy "senders can delete own messages"
on public.messages for delete to authenticated
using ((select auth.uid()) = sender_id);

grant select, insert, update on public.profiles to authenticated;
grant select, insert on public.conversations to authenticated;
grant select, insert on public.conversation_members to authenticated;
grant select, insert, update, delete on public.messages to authenticated;

-- Profile creation is intentionally performed by the authenticated client after
-- signup; avoid an auth.users trigger with elevated privileges in this baseline.

commit;
