begin;
alter table public.messages add column if not exists attachment_path text, add column if not exists attachment_mime_type text, add column if not exists attachment_name text, add column if not exists attachment_purged_at timestamptz;
create table if not exists public.message_media_download_confirmations(
 message_id uuid not null references public.messages(id) on delete cascade,
 conversation_id uuid not null references public.conversations(id) on delete cascade,
 recipient_id uuid not null references auth.users(id) on delete cascade,
 confirmed_at timestamptz not null default now(),
 primary key(message_id,recipient_id));
create index if not exists message_media_download_confirmations_conversation_idx on public.message_media_download_confirmations(conversation_id,message_id);
create table if not exists public.message_media_cleanup_queue(
 message_id uuid primary key references public.messages(id) on delete cascade,
 conversation_id uuid not null references public.conversations(id) on delete cascade,
 bucket_id text not null default 'chat-attachments' check(bucket_id='chat-attachments'),
 object_path text not null check(object_path<>'' and object_path !~ '(^/|(^|/)\.\.?(/|$)|\\\\)'),
 created_at timestamptz not null default now(),
 expires_at timestamptz not null default(now()+interval '14 days'),
 status text not null default 'pending' check(status in('pending','claimed','purged')),
 claimed_at timestamptz,purged_at timestamptz,last_error text);
create index if not exists message_media_cleanup_due_idx on public.message_media_cleanup_queue(status,expires_at);
alter table public.message_media_download_confirmations enable row level security;
alter table public.message_media_cleanup_queue enable row level security;
revoke all on public.message_media_download_confirmations from anon,authenticated;
revoke all on public.message_media_cleanup_queue from anon,authenticated;
grant all on public.message_media_download_confirmations to service_role;
grant all on public.message_media_cleanup_queue to service_role;

create or replace function public.enqueue_message_media_cleanup() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if new.attachment_path is null or btrim(new.attachment_path)='' then
  if tg_op='UPDATE' and old.attachment_path is not null then delete from public.message_media_cleanup_queue where message_id=new.id and status<>'purged'; end if;
  return new;
 end if;
 if split_part(new.attachment_path,'/',1)<>new.conversation_id::text
 or split_part(new.attachment_path,'/',2)<>new.sender_id::text
 or array_length(string_to_array(new.attachment_path,'/'),1)<3
 or new.attachment_path ~ '(^/|(^|/)\.\.?(/|$)|\\\\)' then raise exception 'Invalid private attachment object path'; end if;
 insert into public.message_media_cleanup_queue(message_id,conversation_id,object_path) values(new.id,new.conversation_id,new.attachment_path)
 on conflict(message_id) do update set conversation_id=excluded.conversation_id,object_path=excluded.object_path,status=case when public.message_media_cleanup_queue.status='purged' then 'purged' else 'pending' end,claimed_at=null,last_error=null;
 return new;
end $$;
drop trigger if exists enqueue_message_media_cleanup on public.messages;
create trigger enqueue_message_media_cleanup after insert or update of attachment_path on public.messages for each row execute function public.enqueue_message_media_cleanup();

create or replace function public.confirm_message_media_download(p_message_id uuid,p_recipient_id uuid) returns timestamptz language plpgsql security definer set search_path=public,pg_temp as $$
declare v_conversation_id uuid; v_sender_id uuid; v_confirmed_at timestamptz;
begin
 if p_message_id is null or p_recipient_id is null then raise exception 'Message and recipient are required' using errcode='22004'; end if;
 select m.conversation_id,m.sender_id into v_conversation_id,v_sender_id from public.messages m join public.message_media_cleanup_queue q on q.message_id=m.id
 where m.id=p_message_id and m.attachment_path is not null and m.attachment_purged_at is null and q.status<>'purged';
 if v_conversation_id is null then raise exception 'Attachment not found or already purged' using errcode='P0002'; end if;
 if p_recipient_id=v_sender_id then raise exception 'Sender cannot confirm as a recipient' using errcode='22023'; end if;
 if not exists(select 1 from public.conversation_members cm where cm.conversation_id=v_conversation_id and cm.user_id=p_recipient_id) then raise exception 'Recipient is not a conversation member' using errcode='42501'; end if;
 insert into public.message_media_download_confirmations(message_id,conversation_id,recipient_id) values(p_message_id,v_conversation_id,p_recipient_id)
 on conflict(message_id,recipient_id) do update set confirmed_at=public.message_media_download_confirmations.confirmed_at returning confirmed_at into v_confirmed_at;
 return v_confirmed_at;
end $$;

create or replace function public.claim_chat_media_cleanup(p_limit integer default 50) returns table(message_id uuid,conversation_id uuid,bucket_id text,object_path text)
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if p_limit<1 or p_limit>200 then raise exception 'p_limit must be between 1 and 200' using errcode='22023'; end if;
 return query with eligible as(
  select q.message_id from public.message_media_cleanup_queue q join public.messages m on m.id=q.message_id
  where (q.status='pending' or(q.status='claimed' and q.claimed_at<now()-interval '10 minutes'))
  and(q.expires_at<=now() or not exists(select 1 from public.conversation_members cm where cm.conversation_id=q.conversation_id and cm.user_id<>m.sender_id and not exists(select 1 from public.message_media_download_confirmations dc where dc.message_id=q.message_id and dc.recipient_id=cm.user_id)))
  and m.attachment_purged_at is null order by q.expires_at,q.created_at for update of q skip locked limit p_limit)
 update public.message_media_cleanup_queue q set status='claimed',claimed_at=now(),last_error=null from eligible e where q.message_id=e.message_id
 returning q.message_id,q.conversation_id,q.bucket_id,q.object_path;
end $$;

create or replace function public.finish_chat_media_cleanup(p_message_id uuid,p_success boolean,p_error text default null) returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if p_success then
  update public.message_media_cleanup_queue set status='purged',purged_at=coalesce(purged_at,now()),claimed_at=null,last_error=null where message_id=p_message_id and status='claimed';
  if not found then return false; end if;
  update public.messages set attachment_purged_at=coalesce(attachment_purged_at,now()) where id=p_message_id; return true;
 end if;
 update public.message_media_cleanup_queue set status='pending',claimed_at=null,last_error=left(coalesce(p_error,'Storage deletion failed'),500) where message_id=p_message_id and status='claimed'; return found;
end $$;
revoke all on function public.confirm_message_media_download(uuid,uuid) from public,anon,authenticated;
revoke all on function public.claim_chat_media_cleanup(integer) from public,anon,authenticated;
revoke all on function public.finish_chat_media_cleanup(uuid,boolean,text) from public,anon,authenticated;
grant execute on function public.confirm_message_media_download(uuid,uuid) to service_role;
grant execute on function public.claim_chat_media_cleanup(integer) to service_role;
grant execute on function public.finish_chat_media_cleanup(uuid,boolean,text) to service_role;
commit;