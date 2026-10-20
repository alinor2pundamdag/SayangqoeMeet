begin;

alter table public.messages
  add column if not exists attachment_path text,
  add column if not exists attachment_mime_type text,
  add column if not exists attachment_name text;

insert into storage.buckets (id, name, public)
values ('chat-attachments', 'chat-attachments', false)
on conflict (id) do update set public = false;

-- Storage object names are <conversation UUID>/<uploader UUID>/<random filename>.
-- All access is checked against current conversation membership.
drop policy if exists "Conversation members upload chat attachments" on storage.objects;
create policy "Conversation members upload chat attachments"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'chat-attachments'
  and split_part(name, '/', 1) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
  and split_part(name, '/', 2) = auth.uid()::text
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = split_part(name, '/', 1)::uuid
      and cm.user_id = auth.uid()
  )
);

drop policy if exists "Conversation members read chat attachments" on storage.objects;
create policy "Conversation members read chat attachments"
on storage.objects for select to authenticated
using (
  bucket_id = 'chat-attachments'
  and split_part(name, '/', 1) ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = split_part(name, '/', 1)::uuid
      and cm.user_id = auth.uid()
  )
);

drop policy if exists "Uploader deletes own chat attachments" on storage.objects;
create policy "Uploader deletes own chat attachments"
on storage.objects for delete to authenticated
using (
  bucket_id = 'chat-attachments'
  and split_part(name, '/', 2) = auth.uid()::text
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = split_part(name, '/', 1)::uuid
      and cm.user_id = auth.uid()
  )
);

commit;
