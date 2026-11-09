create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_username text := lower(coalesce(new.raw_user_meta_data ->> 'username', ''));
  v_fallback text := 'user_' || substr(replace(new.id::text, '-', ''), 1, 12);
begin
  if v_username !~ '^[a-z0-9_.]{3,30}$'
     or exists (
       select 1
       from public.profiles p
       where lower(p.username) = v_username
     ) then
    v_username := v_fallback;
  end if;

  insert into public.profiles (id, username, display_name)
  values (
    new.id,
    v_username,
    coalesce(
      nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''),
      nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''),
      v_username
    )
  )
  on conflict (id) do nothing;

  return new;
end;
$function$;
