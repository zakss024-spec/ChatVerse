drop policy if exists reactions_update_self on public.message_reactions;
create policy reactions_update_self_member
on public.message_reactions
for update
to authenticated
using (
  user_id = (select auth.uid())
  and exists (
    select 1
    from public.messages m
    where m.id = message_id
      and private.is_conversation_member(m.conversation_id)
  )
)
with check (
  user_id = (select auth.uid())
  and exists (
    select 1
    from public.messages m
    where m.id = message_id
      and private.is_conversation_member(m.conversation_id)
  )
);

drop policy if exists receipts_update_self on public.message_receipts;
create policy receipts_update_self_member
on public.message_receipts
for update
to authenticated
using (
  user_id = (select auth.uid())
  and exists (
    select 1
    from public.messages m
    where m.id = message_id
      and private.is_conversation_member(m.conversation_id)
  )
)
with check (
  user_id = (select auth.uid())
  and exists (
    select 1
    from public.messages m
    where m.id = message_id
      and private.is_conversation_member(m.conversation_id)
  )
);
