-- Finalize private chat attachments, pinned-message realtime, and missing FK indexes.
-- Safe to apply repeatedly; does not alter existing message or call data.
begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('chat-attachments', 'chat-attachments', false, 52428800,
  array['image/jpeg','image/png','image/webp','audio/mp4','audio/mpeg','audio/ogg','audio/webm','audio/wav','video/mp4','video/webm','video/quicktime']::text[])
on conflict (id) do update set public=false, file_size_limit=excluded.file_size_limit, allowed_mime_types=excluded.allowed_mime_types;

drop policy if exists "Conversation members can read chat attachments" on storage.objects;
create policy "Conversation members can read chat attachments" on storage.objects for select to authenticated
using (bucket_id='chat-attachments' and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
and exists (select 1 from public.conversation_members cm where cm.conversation_id=((storage.foldername(name))[1])::uuid and cm.user_id=(select auth.uid())));

drop policy if exists "Conversation members can upload own chat attachments" on storage.objects;
create policy "Conversation members can upload own chat attachments" on storage.objects for insert to authenticated
with check (bucket_id='chat-attachments' and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
and (storage.foldername(name))[2]=(select auth.uid())::text
and exists (select 1 from public.conversation_members cm where cm.conversation_id=((storage.foldername(name))[1])::uuid and cm.user_id=(select auth.uid())));

drop policy if exists "Conversation members can delete own chat attachments" on storage.objects;
create policy "Conversation members can delete own chat attachments" on storage.objects for delete to authenticated
using (bucket_id='chat-attachments' and (storage.foldername(name))[1] ~* '^[0-9a-f-]{36}$'
and (storage.foldername(name))[2]=(select auth.uid())::text
and exists (select 1 from public.conversation_members cm where cm.conversation_id=((storage.foldername(name))[1])::uuid and cm.user_id=(select auth.uid())));

-- Reconstruct the pinned-message table in a clean database. The hosted
-- project already had this table, but its creation was absent from migration history.
create table if not exists public.pinned_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  message_id uuid not null references public.messages(id) on delete cascade,
  pinned_by uuid not null references auth.users(id),
  pinned_at timestamptz default now(),
  constraint unique_pinned_message unique (conversation_id, message_id)
);

alter table public.pinned_messages enable row level security;
revoke all on public.pinned_messages from anon, authenticated;
grant select, insert, delete on public.pinned_messages to authenticated;

do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='pinned_messages') then
    alter publication supabase_realtime add table public.pinned_messages;
  end if;
end $$;

create index if not exists call_ice_candidates_sender_id_idx on public.call_ice_candidates(sender_id);
create index if not exists pinned_messages_pinned_by_idx on public.pinned_messages(pinned_by);

drop policy if exists "Allow members to pin messages" on public.pinned_messages;
create policy "Allow members to pin messages" on public.pinned_messages for insert to authenticated
with check ((select auth.uid())=pinned_by
and exists (select 1 from public.conversation_members cm where cm.conversation_id=pinned_messages.conversation_id and cm.user_id=(select auth.uid()))
and exists (select 1 from public.messages m where m.id=pinned_messages.message_id and m.conversation_id=pinned_messages.conversation_id));

drop policy if exists "Allow members to unpin messages" on public.pinned_messages;
create policy "Allow members to unpin messages" on public.pinned_messages for delete to authenticated
using (exists (select 1 from public.conversation_members cm where cm.conversation_id=pinned_messages.conversation_id and cm.user_id=(select auth.uid())));

drop policy if exists "Allow members to view pinned messages" on public.pinned_messages;
create policy "Allow members to view pinned messages" on public.pinned_messages for select to authenticated
using (exists (select 1 from public.conversation_members cm where cm.conversation_id=pinned_messages.conversation_id and cm.user_id=(select auth.uid())));

commit;