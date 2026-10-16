begin;

create table if not exists public.conversation_invites (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    inviter_id uuid not null references auth.users(id) on delete cascade,
    recipient_id uuid not null references auth.users(id) on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
    created_at timestamptz not null default now(),
    responded_at timestamptz,
    constraint conversation_invites_not_self check (inviter_id <> recipient_id),
    constraint conversation_invites_unique_recipient unique (conversation_id, recipient_id)
);

create index if not exists conversation_invites_recipient_status_idx
    on public.conversation_invites(recipient_id, status, created_at desc);
create index if not exists conversation_invites_inviter_idx
    on public.conversation_invites(inviter_id, created_at desc);

alter table public.conversation_invites enable row level security;

drop policy if exists "participants can view their conversation invites" on public.conversation_invites;
create policy "participants can view their conversation invites"
on public.conversation_invites for select to authenticated
using ((select auth.uid()) = inviter_id or (select auth.uid()) = recipient_id);

grant select on public.conversation_invites to authenticated;

commit;
