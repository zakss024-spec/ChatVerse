-- Community core: separate community spaces from existing group conversations.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to authenticated;

create table if not exists public.communities (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(trim(name)) between 3 and 60),
  description text not null default '' check (char_length(description) <= 500),
  is_public boolean not null default true,
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table if not exists public.community_members (
  community_id uuid not null references public.communities(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner','admin','member')),
  joined_at timestamptz not null default now(),
  primary key (community_id, user_id)
);
create table if not exists public.community_channels (
  id uuid primary key default gen_random_uuid(),
  community_id uuid not null references public.communities(id) on delete cascade,
  name text not null check (char_length(trim(name)) between 2 and 40),
  description text not null default '' check (char_length(description) <= 240),
  channel_type text not null default 'general' check (channel_type in ('general','announcement')),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  unique (community_id, name)
);
create table if not exists public.community_posts (
  id uuid primary key default gen_random_uuid(),
  channel_id uuid not null references public.community_channels(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  content text not null check (char_length(trim(content)) between 1 and 4000),
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  deleted_at timestamptz
);
create index if not exists community_members_user_idx on public.community_members(user_id, joined_at desc);
create index if not exists community_channels_community_idx on public.community_channels(community_id, created_at);
create index if not exists community_posts_channel_idx on public.community_posts(channel_id, created_at desc);

create or replace function private.is_community_member(p_community_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$ select exists(select 1 from public.community_members cm where cm.community_id=p_community_id and cm.user_id=(select auth.uid())); $$;
create or replace function private.is_community_admin(p_community_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$ select exists(select 1 from public.community_members cm where cm.community_id=p_community_id and cm.user_id=(select auth.uid()) and cm.role in ('owner','admin')); $$;
revoke all on function private.is_community_member(uuid) from public, anon;
revoke all on function private.is_community_admin(uuid) from public, anon;
grant execute on function private.is_community_member(uuid) to authenticated;
grant execute on function private.is_community_admin(uuid) to authenticated;

alter table public.communities enable row level security;
alter table public.community_members enable row level security;
alter table public.community_channels enable row level security;
alter table public.community_posts enable row level security;
grant select, insert, update on public.communities to authenticated;
grant select on public.community_members to authenticated;
grant select, insert, update, delete on public.community_channels to authenticated;
grant select, insert, update on public.community_posts to authenticated;

drop policy if exists communities_read on public.communities;
create policy communities_read on public.communities for select to authenticated using (is_public or private.is_community_member(id));
drop policy if exists communities_create on public.communities;
create policy communities_create on public.communities for insert to authenticated with check (created_by=(select auth.uid()));
drop policy if exists communities_admin_update on public.communities;
create policy communities_admin_update on public.communities for update to authenticated using (private.is_community_admin(id)) with check (private.is_community_admin(id));
drop policy if exists community_members_read on public.community_members;
create policy community_members_read on public.community_members for select to authenticated using (private.is_community_member(community_id));
drop policy if exists community_channels_read on public.community_channels;
create policy community_channels_read on public.community_channels for select to authenticated using (private.is_community_member(community_id));
drop policy if exists community_channels_admin_insert on public.community_channels;
create policy community_channels_admin_insert on public.community_channels for insert to authenticated with check (created_by=(select auth.uid()) and private.is_community_admin(community_id));
drop policy if exists community_channels_admin_update on public.community_channels;
create policy community_channels_admin_update on public.community_channels for update to authenticated using (private.is_community_admin(community_id)) with check (private.is_community_admin(community_id));
drop policy if exists community_channels_admin_delete on public.community_channels;
create policy community_channels_admin_delete on public.community_channels for delete to authenticated using (private.is_community_admin(community_id));
drop policy if exists community_posts_read on public.community_posts;
create policy community_posts_read on public.community_posts for select to authenticated using (exists(select 1 from public.community_channels cc where cc.id=channel_id and private.is_community_member(cc.community_id)));
drop policy if exists community_posts_create on public.community_posts;
create policy community_posts_create on public.community_posts for insert to authenticated with check (author_id=(select auth.uid()) and exists(select 1 from public.community_channels cc where cc.id=channel_id and private.is_community_member(cc.community_id) and (cc.channel_type<>'announcement' or private.is_community_admin(cc.community_id))));
drop policy if exists community_posts_edit on public.community_posts;
create policy community_posts_edit on public.community_posts for update to authenticated using (author_id=(select auth.uid()) or exists(select 1 from public.community_channels cc where cc.id=channel_id and private.is_community_admin(cc.community_id))) with check (author_id=(select auth.uid()) or exists(select 1 from public.community_channels cc where cc.id=channel_id and private.is_community_admin(cc.community_id)));

create or replace function public.create_community(p_name text,p_description text default '')
returns uuid language plpgsql security definer set search_path = ''
as $$
declare v_id uuid;
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if char_length(trim(coalesce(p_name,''))) not between 3 and 60 then raise exception 'Community name must be 3-60 characters'; end if;
 insert into public.communities(name,description,created_by) values(trim(p_name),left(coalesce(p_description,''),500),auth.uid()) returning id into v_id;
 insert into public.community_members(community_id,user_id,role) values(v_id,auth.uid(),'owner');
 insert into public.community_channels(community_id,name,description,channel_type,created_by) values(v_id,'general','General discussion','general',auth.uid());
 return v_id;
end; $$;
create or replace function public.join_community(p_community_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $$
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if not exists(select 1 from public.communities c where c.id=p_community_id and c.is_public) then raise exception 'Community is unavailable'; end if;
 insert into public.community_members(community_id,user_id,role) values(p_community_id,auth.uid(),'member') on conflict(community_id,user_id) do nothing;
 return true;
end; $$;
create or replace function public.leave_community(p_community_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $$
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if exists(select 1 from public.community_members where community_id=p_community_id and user_id=auth.uid() and role='owner') then raise exception 'Owner must transfer ownership before leaving'; end if;
 delete from public.community_members where community_id=p_community_id and user_id=auth.uid();
 return true;
end; $$;
create or replace function public.create_community_channel(p_community_id uuid,p_name text,p_description text default '',p_type text default 'general')
returns uuid language plpgsql security definer set search_path = ''
as $$
declare v_id uuid;
begin
 if auth.uid() is null or not private.is_community_admin(p_community_id) then raise exception 'Admin access required'; end if;
 if char_length(trim(coalesce(p_name,''))) not between 2 and 40 then raise exception 'Channel name must be 2-40 characters'; end if;
 if p_type not in ('general','announcement') then raise exception 'Invalid channel type'; end if;
 insert into public.community_channels(community_id,name,description,channel_type,created_by) values(p_community_id,trim(p_name),left(coalesce(p_description,''),240),p_type,auth.uid()) returning id into v_id;
 return v_id;
end; $$;
revoke all on function public.create_community(text,text) from public,anon;
revoke all on function public.join_community(uuid) from public,anon;
revoke all on function public.leave_community(uuid) from public,anon;
revoke all on function public.create_community_channel(uuid,text,text,text) from public,anon;
grant execute on function public.create_community(text,text) to authenticated;
grant execute on function public.join_community(uuid) to authenticated;
grant execute on function public.leave_community(uuid) to authenticated;
grant execute on function public.create_community_channel(uuid,text,text,text) to authenticated;
