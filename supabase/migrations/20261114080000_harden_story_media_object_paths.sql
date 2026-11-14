-- Enforce canonical private object keys for new Story media rows.
-- Existing rows are left readable; new rows must use the owner's UUID and story UUID
-- as the first two path segments so Storage RLS can safely resolve the story.
alter table public.stories
  add constraint stories_media_path_matches_owner_and_story
  check (
    (story_type = 'text' and media_path is null)
    or (
      story_type = 'image'
      and media_path in (
        user_id::text || '/' || id::text || '/media.jpg',
        user_id::text || '/' || id::text || '/media.png',
        user_id::text || '/' || id::text || '/media.webp'
      )
    )
    or (
      story_type = 'video'
      and media_path = user_id::text || '/' || id::text || '/media.mp4'
    )
  ) not valid;

comment on constraint stories_media_path_matches_owner_and_story on public.stories
  is 'New Story media paths are canonical owner/story UUID paths, matching private Storage RLS.';
