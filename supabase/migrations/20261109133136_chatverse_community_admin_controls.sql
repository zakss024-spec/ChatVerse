-- Community admin operations and safer post permissions.
revoke update on public.community_posts from authenticated;
drop policy if exists community_posts_edit on public.community_posts;

create or replace function public.set_community_member_role(p_community_id uuid,p_member_id uuid,p_role text)
returns boolean language plpgsql security definer set search_path=''
as $fn$
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if p_role not in ('admin','member') then raise exception 'Invalid role'; end if;
 if not exists(select 1 from public.community_members where community_id=p_community_id and user_id=auth.uid() and role='owner') then raise exception 'Owner access required'; end if;
 if not exists(select 1 from public.community_members where community_id=p_community_id and user_id=p_member_id and role<>'owner') then raise exception 'Member not found or cannot change owner'; end if;
 update public.community_members set role=p_role where community_id=p_community_id and user_id=p_member_id;
 return true;
end; $fn$;

create or replace function public.remove_community_member(p_community_id uuid,p_member_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $fn$
declare actor_role text; target_role text;
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 select role into actor_role from public.community_members where community_id=p_community_id and user_id=auth.uid();
 select role into target_role from public.community_members where community_id=p_community_id and user_id=p_member_id;
 if actor_role not in ('owner','admin') or target_role is null or target_role='owner' then raise exception 'Not allowed'; end if;
 if actor_role='admin' and target_role='admin' then raise exception 'Only owner can remove admins'; end if;
 delete from public.community_members where community_id=p_community_id and user_id=p_member_id;
 return true;
end; $fn$;

create or replace function public.transfer_community_ownership(p_community_id uuid,p_new_owner_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $fn$
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if not exists(select 1 from public.community_members where community_id=p_community_id and user_id=auth.uid() and role='owner') then raise exception 'Owner access required'; end if;
 if not exists(select 1 from public.community_members where community_id=p_community_id and user_id=p_new_owner_id) then raise exception 'New owner must be a member'; end if;
 update public.community_members set role='admin' where community_id=p_community_id and user_id=auth.uid();
 update public.community_members set role='owner' where community_id=p_community_id and user_id=p_new_owner_id;
 return true;
end; $fn$;

create or replace function public.delete_community(p_community_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $fn$
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if not exists(select 1 from public.community_members where community_id=p_community_id and user_id=auth.uid() and role='owner') then raise exception 'Owner access required'; end if;
 delete from public.communities where id=p_community_id;
 return true;
end; $fn$;

revoke all on function public.set_community_member_role(uuid,uuid,text) from public,anon;
revoke all on function public.remove_community_member(uuid,uuid) from public,anon;
revoke all on function public.transfer_community_ownership(uuid,uuid) from public,anon;
revoke all on function public.delete_community(uuid) from public,anon;
grant execute on function public.set_community_member_role(uuid,uuid,text) to authenticated;
grant execute on function public.remove_community_member(uuid,uuid) to authenticated;
grant execute on function public.transfer_community_ownership(uuid,uuid) to authenticated;
grant execute on function public.delete_community(uuid) to authenticated;
