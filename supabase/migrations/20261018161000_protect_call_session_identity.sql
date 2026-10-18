-- Prevent a participant from rewriting the identity or media type of an existing call.
begin;
create or replace function public.prevent_call_session_identity_change()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if new.id is distinct from old.id
     or new.caller_id is distinct from old.caller_id
     or new.callee_id is distinct from old.callee_id
     or new.call_type is distinct from old.call_type
     or new.created_at is distinct from old.created_at then
    raise exception 'Call participant identity and type are immutable' using errcode = '42501';
  end if;
  return new;
end;
$$;
drop trigger if exists call_sessions_identity_immutable on public.call_sessions;
create trigger call_sessions_identity_immutable
before update on public.call_sessions
for each row execute function public.prevent_call_session_identity_change();
revoke all on function public.prevent_call_session_identity_change() from public, anon, authenticated;
commit;
