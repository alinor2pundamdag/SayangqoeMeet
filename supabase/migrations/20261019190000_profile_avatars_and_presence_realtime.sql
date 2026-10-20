-- Profile avatar storage and Realtime publication for presence updates.
begin;
insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values ('profile-avatars','profile-avatars',true,8388608,array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public=excluded.public,file_size_limit=excluded.file_size_limit,allowed_mime_types=excluded.allowed_mime_types;
drop policy if exists "Profile avatars are publicly readable" on storage.objects;
create policy "Profile avatars are publicly readable" on storage.objects for select to public using (bucket_id='profile-avatars');
drop policy if exists "Users upload their own profile avatars" on storage.objects;
create policy "Users upload their own profile avatars" on storage.objects for insert to authenticated
with check (bucket_id='profile-avatars' and (storage.foldername(name))[1]=auth.uid()::text);
drop policy if exists "Users update their own profile avatars" on storage.objects;
create policy "Users update their own profile avatars" on storage.objects for update to authenticated
using (bucket_id='profile-avatars' and (storage.foldername(name))[1]=auth.uid()::text)
with check (bucket_id='profile-avatars' and (storage.foldername(name))[1]=auth.uid()::text);
drop policy if exists "Users delete their own profile avatars" on storage.objects;
create policy "Users delete their own profile avatars" on storage.objects for delete to authenticated
using (bucket_id='profile-avatars' and (storage.foldername(name))[1]=auth.uid()::text);
do $$
begin
 if exists(select 1 from pg_publication where pubname='supabase_realtime')
 and not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='user_presence') then
  alter publication supabase_realtime add table public.user_presence;
 end if;
end $$;
commit;
