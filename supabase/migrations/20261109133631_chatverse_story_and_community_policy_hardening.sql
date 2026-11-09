-- Keep community creation behind the atomic owner-provisioning RPC.
revoke insert on public.communities from authenticated;
drop policy if exists communities_create on public.communities;

-- Story creation timestamps and expiry are controlled by server defaults.
revoke update on public.stories from authenticated;
drop policy if exists stories_update_own on public.stories;
drop policy if exists stories_create_own on public.stories;
create policy stories_create_own on public.stories for insert to authenticated
with check (
 user_id=(select auth.uid())
 and created_at <= now() + interval '1 minute'
 and expires_at > now()
 and expires_at <= now() + interval '24 hours'
);

-- A story owner cannot inflate its own view/reaction/reply metrics.
drop policy if exists story_reactions_insert_visible on public.story_reactions;
create policy story_reactions_insert_visible on public.story_reactions for insert to authenticated
with check(user_id=(select auth.uid()) and private.can_view_story(story_id) and not private.is_story_owner(story_id));
drop policy if exists story_replies_insert_visible on public.story_replies;
create policy story_replies_insert_visible on public.story_replies for insert to authenticated
with check(sender_id=(select auth.uid()) and private.can_view_story(story_id) and not private.is_story_owner(story_id));
