
create index if not exists conversations_created_by_idx on public.conversations(created_by);
create index if not exists message_reactions_user_idx on public.message_reactions(user_id);
create index if not exists messages_reply_to_idx on public.messages(reply_to_id);
create index if not exists user_blocks_blocked_user_idx on public.user_blocks(blocked_user_id);
create index if not exists user_reports_message_idx on public.user_reports(message_id);
create index if not exists user_reports_reported_user_idx on public.user_reports(reported_user_id);
create index if not exists user_reports_reporter_idx on public.user_reports(reporter_id);

create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, username, display_name)
  values (
    new.id,
    'user_' || substr(replace(new.id::text, '-', ''), 1, 12),
    coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), nullif(new.raw_user_meta_data ->> 'full_name', ''), '')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;
revoke all on function private.handle_new_user() from public, anon, authenticated;
drop trigger if exists on_auth_user_created_chatverse on auth.users;
create trigger on_auth_user_created_chatverse
after insert on auth.users
for each row execute function private.handle_new_user();

create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
revoke all on function private.set_updated_at() from public, anon, authenticated;
drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at before update on public.profiles
for each row execute function private.set_updated_at();
drop trigger if exists conversations_set_updated_at on public.conversations;
create trigger conversations_set_updated_at before update on public.conversations
for each row execute function private.set_updated_at();

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('avatars', 'avatars', true, 5242880, array['image/jpeg','image/png','image/webp']),
  ('chat-media', 'chat-media', false, 20971520, array['image/jpeg','image/png','image/webp','video/mp4','application/pdf'])
on conflict (id) do nothing;

create policy "avatar_read_public" on storage.objects
for select to public using (bucket_id = 'avatars');
create policy "avatar_upload_own_folder" on storage.objects
for insert to authenticated with check (
  bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text
);
create policy "avatar_update_own_folder" on storage.objects
for update to authenticated using (
  bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text
) with check (
  bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text
);
create policy "avatar_delete_own_folder" on storage.objects
for delete to authenticated using (
  bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text
);

create policy "chat_media_read_members" on storage.objects
for select to authenticated using (
  bucket_id = 'chat-media'
  and (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  and private.is_conversation_member(((storage.foldername(name))[1])::uuid)
);
create policy "chat_media_upload_members" on storage.objects
for insert to authenticated with check (
  bucket_id = 'chat-media'
  and (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  and private.is_conversation_member(((storage.foldername(name))[1])::uuid)
);
create policy "chat_media_update_owner_members" on storage.objects
for update to authenticated using (
  bucket_id = 'chat-media'
  and owner_id = (select auth.uid())::text
  and (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  and private.is_conversation_member(((storage.foldername(name))[1])::uuid)
) with check (
  bucket_id = 'chat-media'
  and owner_id = (select auth.uid())::text
  and (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  and private.is_conversation_member(((storage.foldername(name))[1])::uuid)
);
create policy "chat_media_delete_owner_members" on storage.objects
for delete to authenticated using (
  bucket_id = 'chat-media'
  and owner_id = (select auth.uid())::text
  and (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
  and private.is_conversation_member(((storage.foldername(name))[1])::uuid)
);
