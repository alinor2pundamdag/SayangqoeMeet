-- Atomic get-or-create for a one-to-one conversation.
-- Uses the existing schema: conversations.is_group (false = direct), title,
-- created_by, and conversation_members(conversation_id, user_id).
-- A transaction-scoped advisory lock serializes calls for the same unordered user pair.
begin;

create or replace function public.get_or_create_direct_conversation(
  p_user_1 uuid,
  p_user_2 uuid
)
returns table (
  conversation_id uuid,
  is_new boolean
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_conv_id uuid;
  v_created boolean := false;
  v_low uuid;
  v_high uuid;
begin
  if p_user_1 is null or p_user_2 is null then
    raise exception 'Both user IDs are required' using errcode = '22004';
  end if;

  if p_user_1 = p_user_2 then
    raise exception 'Cannot create direct conversation with oneself' using errcode = '22023';
  end if;

  v_low := least(p_user_1, p_user_2);
  v_high := greatest(p_user_1, p_user_2);

  -- Stable lock key regardless of which user initiated the request.
  perform pg_advisory_xact_lock(
    hashtextextended(v_low::text || ':' || v_high::text, 0)
  );

  -- Reuse only a non-group conversation with exactly these two members.
  select c.id
    into v_conv_id
  from public.conversations c
  where c.is_group = false
    and exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = c.id and cm.user_id = p_user_1
    )
    and exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = c.id and cm.user_id = p_user_2
    )
    and (
      select count(distinct cm.user_id)
      from public.conversation_members cm
      where cm.conversation_id = c.id
    ) = 2
  order by c.created_at asc, c.id asc
  limit 1;

  if v_conv_id is null then
    insert into public.conversations (is_group, title, created_by)
    values (false, null, p_user_1)
    returning id into v_conv_id;

    insert into public.conversation_members (conversation_id, user_id)
    values (v_conv_id, p_user_1), (v_conv_id, p_user_2);

    v_created := true;
  end if;

  return query select v_conv_id, v_created;
end;
$$;

-- Edge Function uses service-role client. Do not expose direct RPC to clients.
revoke all on function public.get_or_create_direct_conversation(uuid, uuid) from public;
revoke all on function public.get_or_create_direct_conversation(uuid, uuid) from anon;
revoke all on function public.get_or_create_direct_conversation(uuid, uuid) from authenticated;
grant execute on function public.get_or_create_direct_conversation(uuid, uuid) to service_role;

commit;
