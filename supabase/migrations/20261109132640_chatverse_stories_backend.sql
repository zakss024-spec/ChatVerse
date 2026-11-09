-- Stories, Close Friends, reactions, replies, viewers and private media storage.
create table if not exists public.stories (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references auth.users(id) on delete cascade,
 story_type text not null check (story_type in ('image','video','text')),
 text_content text not null default '' check (char_length(text_content) <= 2000),
 caption text not null default '' check (char_length(caption) <= 500),
 media_path text,
 audience text not null default 'everyone' check (audience in ('everyone','close_friends')),
 created_at timestamptz not null default now(),
 expires_at timestamptz not null default (now() + interval '24 hours'),
 constraint story_media_required check ((story_type='text' and media_path is null) or (story_type in ('image','video') and media_path is not null))
);
create index if not exists stories_active_idx on public.stories(expires_at desc, created_at desc);
create table if not exists public.story_close_friends(owner_id uuid not null references auth.users(id) on delete cascade, friend_id uuid not null references auth.users(id) on delete cascade, created_at timestamptz not null default now(), primary key(owner_id,friend_id), check(owner_id <> friend_id));
create table if not exists public.story_views(story_id uuid not null references public.stories(id) on delete cascade, viewer_id uuid not null references auth.users(id) on delete cascade, viewed_at timestamptz not null default now(), primary key(story_id,viewer_id));
create table if not exists public.story_reactions(id uuid primary key default gen_random_uuid(), story_id uuid not null references public.stories(id) on delete cascade, user_id uuid not null references auth.users(id) on delete cascade, emoji text not null check (emoji in ('❤️','😂','😮','😢','👏','🔥')), created_at timestamptz not null default now(), unique(story_id,user_id));
create table if not exists public.story_replies(id uuid primary key default gen_random_uuid(), story_id uuid not null references public.stories(id) on delete cascade, sender_id uuid not null references auth.users(id) on delete cascade, content text not null check (char_length(trim(content)) between 1 and 1000), created_at timestamptz not null default now());
create index if not exists story_views_story_idx on public.story_views(story_id,viewed_at desc);
create index if not exists story_replies_story_idx on public.story_replies(story_id,created_at desc);

create or replace function private.is_story_owner(p_story_id uuid)
returns boolean language sql stable security definer set search_path=''
as $fn$ select exists (select 1 from public.stories s where s.id=p_story_id and s.user_id=(select auth.uid())); $fn$;
create or replace function private.can_view_story(p_story_id uuid)
returns boolean language sql stable security definer set search_path=''
as $fn$
 select exists (
  select 1 from public.stories s
  where s.id=p_story_id and s.expires_at>now()
    and (s.user_id=(select auth.uid()) or s.audience='everyone'
      or (s.audience='close_friends' and exists (
        select 1 from public.story_close_friends cf
        where cf.owner_id=s.user_id and cf.friend_id=(select auth.uid())
      ))
    )
 );
$fn$;
revoke all on function private.is_story_owner(uuid) from public,anon;
revoke all on function private.can_view_story(uuid) from public,anon;
grant execute on function private.is_story_owner(uuid) to authenticated;
grant execute on function private.can_view_story(uuid) to authenticated;

alter table public.stories enable row level security;
alter table public.story_close_friends enable row level security;
alter table public.story_views enable row level security;
alter table public.story_reactions enable row level security;
alter table public.story_replies enable row level security;
grant select,insert,update,delete on public.stories to authenticated;
grant select,insert,delete on public.story_close_friends to authenticated;
grant select,insert on public.story_views to authenticated;
grant select,insert,delete on public.story_reactions to authenticated;
grant select,insert on public.story_replies to authenticated;

drop policy if exists stories_read_visible on public.stories;
create policy stories_read_visible on public.stories for select to authenticated using(private.can_view_story(id));
drop policy if exists stories_create_own on public.stories;
create policy stories_create_own on public.stories for insert to authenticated with check(user_id=(select auth.uid()) and expires_at<=created_at+interval '24 hours' and expires_at>created_at);
drop policy if exists stories_update_own on public.stories;
create policy stories_update_own on public.stories for update to authenticated using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));
drop policy if exists stories_delete_own on public.stories;
create policy stories_delete_own on public.stories for delete to authenticated using(user_id=(select auth.uid()));
drop policy if exists story_close_friends_own on public.story_close_friends;
create policy story_close_friends_own on public.story_close_friends for all to authenticated using(owner_id=(select auth.uid())) with check(owner_id=(select auth.uid()));
drop policy if exists story_views_read_owner on public.story_views;
create policy story_views_read_owner on public.story_views for select to authenticated using(private.is_story_owner(story_id));
drop policy if exists story_views_insert_visible on public.story_views;
create policy story_views_insert_visible on public.story_views for insert to authenticated with check(viewer_id=(select auth.uid()) and private.can_view_story(story_id) and not private.is_story_owner(story_id));
drop policy if exists story_reactions_read_visible on public.story_reactions;
create policy story_reactions_read_visible on public.story_reactions for select to authenticated using(private.can_view_story(story_id));
drop policy if exists story_reactions_insert_visible on public.story_reactions;
create policy story_reactions_insert_visible on public.story_reactions for insert to authenticated with check(user_id=(select auth.uid()) and private.can_view_story(story_id));
drop policy if exists story_reactions_delete_own on public.story_reactions;
create policy story_reactions_delete_own on public.story_reactions for delete to authenticated using(user_id=(select auth.uid()));
drop policy if exists story_replies_read_visible on public.story_replies;
create policy story_replies_read_visible on public.story_replies for select to authenticated using(private.can_view_story(story_id));
drop policy if exists story_replies_insert_visible on public.story_replies;
create policy story_replies_insert_visible on public.story_replies for insert to authenticated with check(sender_id=(select auth.uid()) and private.can_view_story(story_id));

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('story-media','story-media',false,20971520,array['image/jpeg','image/png','image/webp','video/mp4'])
on conflict(id) do update set public=false,file_size_limit=20971520,allowed_mime_types=array['image/jpeg','image/png','image/webp','video/mp4'];
drop policy if exists story_media_read_visible on storage.objects;
create policy story_media_read_visible on storage.objects for select to authenticated using(bucket_id='story-media' and private.can_view_story(((storage.foldername(name))[2])::uuid));
drop policy if exists story_media_insert_owner on storage.objects;
create policy story_media_insert_owner on storage.objects for insert to authenticated with check(bucket_id='story-media' and (storage.foldername(name))[1]=(select auth.uid())::text and exists(select 1 from public.stories s where s.id=((storage.foldername(name))[2])::uuid and s.user_id=(select auth.uid()) and s.media_path=name));
drop policy if exists story_media_delete_owner on storage.objects;
create policy story_media_delete_owner on storage.objects for delete to authenticated using(bucket_id='story-media' and (storage.foldername(name))[1]=(select auth.uid())::text and exists(select 1 from public.stories s where s.id=((storage.foldername(name))[2])::uuid and s.user_id=(select auth.uid())));
