create or replace function public.join_global_conversation()
returns uuid
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user uuid := auth.uid();
  v_conversation uuid;
begin
  if v_user is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  select c.id
    into v_conversation
    from public.conversations c
   where c.type = 'global'
     and c.title = 'ChatVerse Global'
   order by c.created_at asc
   limit 1
   for update;

  if v_conversation is null then
    raise exception 'ChatVerse Global room is not configured';
  end if;

  insert into public.conversation_members (conversation_id, user_id, role)
  values (v_conversation, v_user, 'member')
  on conflict (conversation_id, user_id) do nothing;

  return v_conversation;
end;
$function$;

revoke all on function public.join_global_conversation() from public, anon;
grant execute on function public.join_global_conversation() to authenticated;

create or replace function public.create_group_conversation(
  p_title text,
  p_member_ids uuid[] default '{}'::uuid[]
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user uuid := auth.uid();
  v_conversation uuid;
  v_title text := btrim(coalesce(p_title, ''));
  v_member uuid;
begin
  if v_user is null then
    raise exception 'Authentication required';
  end if;
  if length(v_title) < 2 or length(v_title) > 60 then
    raise exception 'Group name must be 2-60 characters';
  end if;
  if coalesce(array_length(p_member_ids, 1), 0) > 99 then
    raise exception 'A group can have at most 100 members';
  end if;

  insert into public.conversations(type, title, created_by)
  values ('group', v_title, v_user)
  returning id into v_conversation;

  insert into public.conversation_members(conversation_id, user_id, role)
  values (v_conversation, v_user, 'admin');

  for v_member in
    select distinct x
      from unnest(coalesce(p_member_ids, '{}'::uuid[])) as t(x)
     where x is not null and x <> v_user
  loop
    if not exists (select 1 from public.profiles p where p.id = v_member) then
      raise exception 'One or more selected users do not exist';
    end if;

    if exists (
      select 1
        from public.user_blocks b
       where (b.blocker_id = v_user and b.blocked_user_id = v_member)
          or (b.blocker_id = v_member and b.blocked_user_id = v_user)
    ) then
      raise exception 'A selected user cannot be added due to a block';
    end if;

    insert into public.conversation_members(conversation_id, user_id, role)
    values (v_conversation, v_member, 'member');
  end loop;

  return v_conversation;
end;
$function$;

create or replace function public.add_group_member(
  p_conversation_id uuid,
  p_member_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_actor uuid := auth.uid();
  v_type text;
  v_count integer;
begin
  if v_actor is null then
    raise exception 'Authentication required';
  end if;
  if p_member_id is null then
    raise exception 'Member is required';
  end if;

  select c.type
    into v_type
    from public.conversations c
   where c.id = p_conversation_id;

  if v_type is distinct from 'group' then
    raise exception 'Group not found';
  end if;
  if not private.is_conversation_admin(p_conversation_id) then
    raise exception 'Only group admins can add members';
  end if;
  if not exists (select 1 from public.profiles p where p.id = p_member_id) then
    raise exception 'User not found';
  end if;

  if exists (
    select 1
      from public.user_blocks b
     where (b.blocker_id = v_actor and b.blocked_user_id = p_member_id)
        or (b.blocker_id = p_member_id and b.blocked_user_id = v_actor)
  ) then
    raise exception 'Cannot add a blocked user';
  end if;

  if exists (
    select 1
      from public.conversation_members cm
     where cm.conversation_id = p_conversation_id
       and cm.user_id = p_member_id
  ) then
    return false;
  end if;

  select count(*)
    into v_count
    from public.conversation_members cm
   where cm.conversation_id = p_conversation_id;

  if v_count >= 100 then
    raise exception 'Group member limit reached';
  end if;

  insert into public.conversation_members(conversation_id, user_id, role)
  values (p_conversation_id, p_member_id, 'member')
  on conflict (conversation_id, user_id) do nothing;

  return found;
end;
$function$;

revoke all on function public.create_group_conversation(text, uuid[]) from public, anon;
revoke all on function public.add_group_member(uuid, uuid) from public, anon;
grant execute on function public.create_group_conversation(text, uuid[]) to authenticated;
grant execute on function public.add_group_member(uuid, uuid) to authenticated;
