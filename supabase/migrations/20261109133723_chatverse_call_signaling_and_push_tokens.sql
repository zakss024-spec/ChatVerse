-- Secure call signaling records and per-user FCM device token registry.
create table if not exists public.call_sessions (
 id uuid primary key default gen_random_uuid(),
 conversation_id uuid not null references public.conversations(id) on delete cascade,
 caller_id uuid not null references auth.users(id) on delete cascade,
 callee_id uuid not null references auth.users(id) on delete cascade,
 call_type text not null check(call_type in ('audio','video')),
 status text not null default 'ringing' check(status in ('ringing','accepted','rejected','ended','missed')),
 created_at timestamptz not null default now(),
 accepted_at timestamptz,
 ended_at timestamptz,
 check(caller_id<>callee_id)
);
create index if not exists call_sessions_participants_idx on public.call_sessions(caller_id,callee_id,created_at desc);
create table if not exists public.call_signals (
 id uuid primary key default gen_random_uuid(),
 call_id uuid not null references public.call_sessions(id) on delete cascade,
 sender_id uuid not null references auth.users(id) on delete cascade,
 signal_type text not null check(signal_type in ('offer','answer','ice_candidate','bye')),
 payload jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now(),
 check(jsonb_typeof(payload)='object')
);
create index if not exists call_signals_call_idx on public.call_signals(call_id,created_at);
create table if not exists public.device_push_tokens (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references auth.users(id) on delete cascade,
 token text not null unique check(char_length(token) between 20 and 4096),
 device_id text not null check(char_length(device_id) between 1 and 128),
 platform text not null default 'android' check(platform in ('android')),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(user_id,device_id)
);
create index if not exists device_push_tokens_user_idx on public.device_push_tokens(user_id);
create or replace function private.is_call_participant(p_call_id uuid)
returns boolean language sql stable security definer set search_path=''
as $$ select exists(select 1 from public.call_sessions c where c.id=p_call_id and (c.caller_id=(select auth.uid()) or c.callee_id=(select auth.uid()))); $$;
revoke all on function private.is_call_participant(uuid) from public,anon;
grant execute on function private.is_call_participant(uuid) to authenticated;
alter table public.call_sessions enable row level security;
alter table public.call_signals enable row level security;
alter table public.device_push_tokens enable row level security;
grant select on public.call_sessions to authenticated;
grant select,insert on public.call_signals to authenticated;
grant select,insert,update,delete on public.device_push_tokens to authenticated;
drop policy if exists call_sessions_participant_read on public.call_sessions;
create policy call_sessions_participant_read on public.call_sessions for select to authenticated using(caller_id=(select auth.uid()) or callee_id=(select auth.uid()));
drop policy if exists call_signals_participant_read on public.call_signals;
create policy call_signals_participant_read on public.call_signals for select to authenticated using(private.is_call_participant(call_id));
drop policy if exists call_signals_participant_insert on public.call_signals;
create policy call_signals_participant_insert on public.call_signals for insert to authenticated with check(sender_id=(select auth.uid()) and private.is_call_participant(call_id));
drop policy if exists device_push_tokens_own on public.device_push_tokens;
create policy device_push_tokens_own on public.device_push_tokens for all to authenticated using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));

create or replace function public.start_call(p_conversation_id uuid,p_callee_id uuid,p_call_type text)
returns uuid language plpgsql security definer set search_path=''
as $fn$
declare v_id uuid;
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 if p_callee_id=auth.uid() or p_call_type not in ('audio','video') then raise exception 'Invalid call request'; end if;
 if not exists(select 1 from public.conversation_members where conversation_id=p_conversation_id and user_id=auth.uid())
   or not exists(select 1 from public.conversation_members where conversation_id=p_conversation_id and user_id=p_callee_id) then
   raise exception 'Both users must belong to the conversation';
 end if;
 if exists(select 1 from public.call_sessions where conversation_id=p_conversation_id and status in ('ringing','accepted')) then
   raise exception 'An active call already exists for this conversation';
 end if;
 insert into public.call_sessions(conversation_id,caller_id,callee_id,call_type) values(p_conversation_id,auth.uid(),p_callee_id,p_call_type) returning id into v_id;
 return v_id;
end; $fn$;

create or replace function public.update_call_status(p_call_id uuid,p_status text)
returns boolean language plpgsql security definer set search_path=''
as $fn$
declare v_call public.call_sessions%rowtype;
begin
 if auth.uid() is null then raise exception 'Authentication required'; end if;
 select * into v_call from public.call_sessions where id=p_call_id for update;
 if not found or (auth.uid()<>v_call.caller_id and auth.uid()<>v_call.callee_id) then raise exception 'Call not found'; end if;
 if p_status='accepted' then
   if auth.uid()<>v_call.callee_id or v_call.status<>'ringing' then raise exception 'Only the callee can accept a ringing call'; end if;
   update public.call_sessions set status='accepted',accepted_at=now() where id=p_call_id;
 elsif p_status in ('rejected','missed') then
   if auth.uid()<>v_call.callee_id or v_call.status<>'ringing' then raise exception 'Only the callee can reject or mark a ringing call missed'; end if;
   update public.call_sessions set status=p_status,ended_at=now() where id=p_call_id;
 elsif p_status='ended' then
   if v_call.status not in ('ringing','accepted') then raise exception 'Call is already finished'; end if;
   update public.call_sessions set status='ended',ended_at=now() where id=p_call_id;
 else raise exception 'Invalid call status';
 end if;
 return true;
end; $fn$;
revoke all on function public.start_call(uuid,uuid,text) from public,anon;
revoke all on function public.update_call_status(uuid,text) from public,anon;
grant execute on function public.start_call(uuid,uuid,text) to authenticated;
grant execute on function public.update_call_status(uuid,text) to authenticated;
do $$
begin
 if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='call_sessions') then alter publication supabase_realtime add table public.call_sessions; end if;
 if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='call_signals') then alter publication supabase_realtime add table public.call_signals; end if;
end $$;
