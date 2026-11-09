
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null,
  display_name text not null default '',
  bio text not null default '',
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index profiles_username_lower_uidx on public.profiles (lower(username));

create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  type text not null check (type in ('direct', 'group')),
  title text,
  created_by uuid not null references auth.users(id) on delete restrict,
  last_message_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint group_requires_title check (type <> 'group' or (title is not null and length(trim(title)) > 0))
);

create table public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner', 'admin', 'member')),
  joined_at timestamptz not null default now(),
  last_read_at timestamptz,
  primary key (conversation_id, user_id)
);
create index conversation_members_user_idx on public.conversation_members(user_id, conversation_id);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete restrict,
  message_type text not null default 'text' check (message_type in ('text', 'image', 'video', 'file', 'system')),
  content text,
  media_path text,
  media_mime_type text,
  media_size_bytes bigint check (media_size_bytes is null or media_size_bytes >= 0),
  reply_to_id uuid references public.messages(id) on delete set null,
  edited_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  constraint message_has_payload check (
    message_type = 'system' or
    (coalesce(length(trim(content)), 0) > 0 or media_path is not null)
  )
);
create index messages_conversation_created_idx on public.messages(conversation_id, created_at desc);
create index messages_sender_idx on public.messages(sender_id);

create table public.message_receipts (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  delivered_at timestamptz,
  read_at timestamptz,
  primary key (message_id, user_id)
);
create index message_receipts_user_idx on public.message_receipts(user_id, message_id);

create table public.message_reactions (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  emoji text not null check (length(emoji) between 1 and 16),
  created_at timestamptz not null default now(),
  unique (message_id, user_id)
);

create table public.user_blocks (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_user_id),
  constraint no_self_block check (blocker_id <> blocked_user_id)
);

create table public.user_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reported_user_id uuid references auth.users(id) on delete set null,
  message_id uuid references public.messages(id) on delete set null,
  reason text not null check (length(trim(reason)) between 3 and 1000),
  status text not null default 'open' check (status in ('open', 'reviewing', 'resolved')),
  created_at timestamptz not null default now()
);

create or replace function private.is_conversation_member(target_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null
    and exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = target_conversation_id
        and cm.user_id = (select auth.uid())
    );
$$;

create or replace function private.is_conversation_admin(target_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select auth.uid()) is not null
    and exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = target_conversation_id
        and cm.user_id = (select auth.uid())
        and cm.role in ('owner', 'admin')
    );
$$;

revoke all on function private.is_conversation_member(uuid) from public, anon;
revoke all on function private.is_conversation_admin(uuid) from public, anon;
grant usage on schema private to authenticated;
grant execute on function private.is_conversation_member(uuid) to authenticated;
grant execute on function private.is_conversation_admin(uuid) to authenticated;

alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;
alter table public.message_receipts enable row level security;
alter table public.message_reactions enable row level security;
alter table public.user_blocks enable row level security;
alter table public.user_reports enable row level security;

grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.conversations to authenticated;
grant select, insert, update, delete on public.conversation_members to authenticated;
grant select, insert, update, delete on public.messages to authenticated;
grant select, insert, update, delete on public.message_receipts to authenticated;
grant select, insert, update, delete on public.message_reactions to authenticated;
grant select, insert, update, delete on public.user_blocks to authenticated;
grant select, insert, update, delete on public.user_reports to authenticated;

create policy "profiles_read_signed_in" on public.profiles
for select to authenticated using (true);
create policy "profiles_insert_self" on public.profiles
for insert to authenticated with check ((select auth.uid()) = id);
create policy "profiles_update_self" on public.profiles
for update to authenticated using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

create policy "conversations_read_members" on public.conversations
for select to authenticated using (private.is_conversation_member(id));
create policy "conversations_create_self" on public.conversations
for insert to authenticated with check ((select auth.uid()) = created_by);
create policy "conversations_update_admins" on public.conversations
for update to authenticated using (private.is_conversation_admin(id))
with check (private.is_conversation_admin(id));
create policy "conversations_delete_admins" on public.conversations
for delete to authenticated using (private.is_conversation_admin(id));

create policy "members_read_conversation_members" on public.conversation_members
for select to authenticated using (private.is_conversation_member(conversation_id));
create policy "members_add_by_creator_or_admin" on public.conversation_members
for insert to authenticated with check (
  exists (
    select 1 from public.conversations c
    where c.id = conversation_id and c.created_by = (select auth.uid())
  ) or private.is_conversation_admin(conversation_id)
);
create policy "members_update_admins" on public.conversation_members
for update to authenticated using (private.is_conversation_admin(conversation_id))
with check (private.is_conversation_admin(conversation_id));
create policy "members_remove_self_or_admin" on public.conversation_members
for delete to authenticated using (
  user_id = (select auth.uid()) or private.is_conversation_admin(conversation_id)
);

create policy "messages_read_members" on public.messages
for select to authenticated using (private.is_conversation_member(conversation_id));
create policy "messages_send_as_self" on public.messages
for insert to authenticated with check (
  sender_id = (select auth.uid()) and private.is_conversation_member(conversation_id)
);
create policy "messages_edit_own" on public.messages
for update to authenticated using (
  sender_id = (select auth.uid()) and private.is_conversation_member(conversation_id)
) with check (
  sender_id = (select auth.uid()) and private.is_conversation_member(conversation_id)
);
create policy "messages_delete_own_or_admin" on public.messages
for delete to authenticated using (
  sender_id = (select auth.uid()) or private.is_conversation_admin(conversation_id)
);

create policy "receipts_read_conversation_members" on public.message_receipts
for select to authenticated using (
  exists (select 1 from public.messages m
    where m.id = message_id and private.is_conversation_member(m.conversation_id))
);
create policy "receipts_insert_self" on public.message_receipts
for insert to authenticated with check (
  user_id = (select auth.uid()) and exists (
    select 1 from public.messages m
    where m.id = message_id and private.is_conversation_member(m.conversation_id)
  )
);
create policy "receipts_update_self" on public.message_receipts
for update to authenticated using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy "reactions_read_members" on public.message_reactions
for select to authenticated using (
  exists (select 1 from public.messages m
    where m.id = message_id and private.is_conversation_member(m.conversation_id))
);
create policy "reactions_add_self" on public.message_reactions
for insert to authenticated with check (
  user_id = (select auth.uid()) and exists (
    select 1 from public.messages m
    where m.id = message_id and private.is_conversation_member(m.conversation_id)
  )
);
create policy "reactions_update_self" on public.message_reactions
for update to authenticated using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
create policy "reactions_delete_self" on public.message_reactions
for delete to authenticated using (user_id = (select auth.uid()));

create policy "blocks_read_own" on public.user_blocks
for select to authenticated using (blocker_id = (select auth.uid()));
create policy "blocks_create_own" on public.user_blocks
for insert to authenticated with check (blocker_id = (select auth.uid()));
create policy "blocks_delete_own" on public.user_blocks
for delete to authenticated using (blocker_id = (select auth.uid()));

create policy "reports_create_self" on public.user_reports
for insert to authenticated with check (reporter_id = (select auth.uid()));
create policy "reports_read_self" on public.user_reports
for select to authenticated using (reporter_id = (select auth.uid()));

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'messages'
  ) then
    alter publication supabase_realtime add table public.messages;
  end if;
end $$;
