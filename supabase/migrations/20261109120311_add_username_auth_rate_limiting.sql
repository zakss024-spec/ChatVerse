create table if not exists public.username_auth_rate_limits (
  key_hash text primary key
    check (key_hash ~ '^[0-9a-f]{64}$'),
  window_started_at timestamptz not null default pg_catalog.now(),
  attempts integer not null default 0
    check (attempts >= 0 and attempts <= 100000),
  blocked_until timestamptz,
  updated_at timestamptz not null default pg_catalog.now()
);

alter table public.username_auth_rate_limits enable row level security;
revoke all on table public.username_auth_rate_limits from public, anon, authenticated;
grant select, insert, update, delete on table public.username_auth_rate_limits to service_role;

create or replace function public.consume_username_auth_attempt(p_key_hash text)
returns table(allowed boolean, blocked_until timestamptz, attempts integer)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_now timestamptz := pg_catalog.now();
begin
  if p_key_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'Invalid rate-limit key';
  end if;

  insert into public.username_auth_rate_limits as limits
    (key_hash, window_started_at, attempts, blocked_until, updated_at)
  values
    (p_key_hash, v_now, 1, null, v_now)
  on conflict (key_hash) do update
    set window_started_at = case
          when limits.window_started_at <= v_now - interval '15 minutes' then v_now
          else limits.window_started_at
        end,
        attempts = case
          when limits.window_started_at <= v_now - interval '15 minutes' then 1
          else limits.attempts + 1
        end,
        blocked_until = case
          when limits.window_started_at <= v_now - interval '15 minutes' then null
          when limits.attempts >= 8 then v_now + interval '15 minutes'
          else limits.blocked_until
        end,
        updated_at = v_now;

  return query
    select
      (r.blocked_until is null or r.blocked_until <= v_now) as allowed,
      r.blocked_until,
      r.attempts
    from public.username_auth_rate_limits r
    where r.key_hash = p_key_hash;
end;
$function$;

revoke all on function public.consume_username_auth_attempt(text) from public, anon, authenticated;
grant execute on function public.consume_username_auth_attempt(text) to service_role;
