-- Membership changes must go through validated SECURITY DEFINER RPCs.
-- Direct table writes bypass group-size, block, owner and role-transition checks.
drop policy if exists members_add_by_creator_or_admin on public.conversation_members;
drop policy if exists members_remove_self_or_admin on public.conversation_members;
drop policy if exists members_update_admins on public.conversation_members;

-- A former conversation member must not be able to mutate reactions in that room.
drop policy if exists reactions_delete_self on public.message_reactions;
create policy reactions_delete_self_member
on public.message_reactions
for delete
to authenticated
using (
  user_id = (select auth.uid())
  and exists (
    select 1
    from public.messages m
    where m.id = message_id
      and private.is_conversation_member(m.conversation_id)
  )
);

-- These functions are invoked by database triggers, not by client RPC.
revoke execute on function private.create_message_receipts() from public, anon, authenticated;
revoke execute on function private.ensure_global_chat_membership() from public, anon, authenticated;
revoke execute on function private.validate_message_reply() from public, anon, authenticated;
