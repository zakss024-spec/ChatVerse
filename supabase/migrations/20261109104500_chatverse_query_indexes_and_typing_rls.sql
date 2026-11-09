create index if not exists conversation_typing_user_idx
  on public.conversation_typing (user_id);

create index if not exists messages_deleted_by_idx
  on public.messages (deleted_by);

create index if not exists messages_reply_to_message_id_idx
  on public.messages (reply_to_message_id);

alter policy "Users can set own typing status"
  on public.conversation_typing
  with check (
    user_id = (select auth.uid())
    and private.is_conversation_member(conversation_id)
  );

alter policy "Users can update own typing status"
  on public.conversation_typing
  using (
    user_id = (select auth.uid())
    and private.is_conversation_member(conversation_id)
  )
  with check (
    user_id = (select auth.uid())
    and private.is_conversation_member(conversation_id)
  );
