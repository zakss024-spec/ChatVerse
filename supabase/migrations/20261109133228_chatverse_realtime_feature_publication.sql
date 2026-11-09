-- Add new feature tables to the existing Realtime publication.
do $$
declare t text;
begin
  foreach t in array array['stories','story_reactions','story_replies','story_views','community_members','community_channels','community_posts'] loop
    if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename=t) then
      execute format('alter publication supabase_realtime add table public.%I',t);
    end if;
  end loop;
end $$;
