-- Resolve chat-list identities on the server and return only conversations
-- in which the authenticated caller is an active member.
begin;

create or replace function public.get_my_conversation_list()
returns table (
    conversation_id uuid,
    title text,
    is_group boolean,
    created_at timestamptz,
    other_user_id uuid,
    other_username text,
    other_display_name text,
    display_label text
)
language plpgsql
security definer
set search_path = public, pg_temp
stable
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    return query
    select
        c.id as conversation_id,
        nullif(btrim(c.title), '') as title,
        c.is_group,
        c.created_at,
        other_member.user_id as other_user_id,
        nullif(btrim(other_profile.username), '') as other_username,
        nullif(btrim(other_profile.display_name), '') as other_display_name,
        case
            when c.is_group then coalesce(nullif(btrim(c.title), ''), 'Group conversation')
            else coalesce(
                nullif(btrim(other_profile.display_name), ''),
                nullif(btrim(other_profile.username), ''),
                'SayangqoeMeet user'
            )
        end as display_label
    from public.conversation_members mine
    join public.conversations c
      on c.id = mine.conversation_id
    left join lateral (
        select cm.user_id
        from public.conversation_members cm
        where cm.conversation_id = c.id
          and cm.user_id <> v_caller
        order by cm.joined_at asc, cm.user_id asc
        limit 1
    ) other_member on true
    left join public.profiles other_profile
      on other_profile.id = other_member.user_id
    where mine.user_id = v_caller
    order by c.created_at desc, c.id desc;
end;
$$;

revoke all on function public.get_my_conversation_list() from public, anon;
grant execute on function public.get_my_conversation_list() to authenticated;

commit;
