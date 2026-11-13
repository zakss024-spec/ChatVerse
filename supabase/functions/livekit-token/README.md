# LiveKit token Edge Function

This function issues short-lived LiveKit room tokens only to authenticated users who are one of the two participants in an active ChatVerse call.

## Required Supabase Edge Function secrets

Configure these in the Supabase project's Edge Function Secrets (never commit their values):

- `LIVEKIT_URL` — LiveKit server URL (for example, the project's secure WebSocket endpoint).
- `LIVEKIT_API_KEY` — LiveKit API key.
- `LIVEKIT_API_SECRET` — LiveKit API secret.

Supabase provides `SUPABASE_URL` and the project's publishable key to Edge Functions.

## Request

Send an authenticated POST request to `/functions/v1/livekit-token` with the project's publishable key in `apikey`, the user's Supabase access token in `Authorization: Bearer ...`, and JSON:

```json
{ "call_id": "UUID from public.start_call(...)" }
```

The function checks the Supabase user session, reads `call_sessions` using that user's JWT (so participant RLS is applied), and only issues a token while the call is `ringing` or `accepted`. The LiveKit API secret remains server-side. Tokens expire after 15 minutes.

## Deployment and verification

Deploy this function to the existing ChatVerse Supabase project after setting the three LiveKit secrets. Do not put LiveKit secrets in the Android app, repository, or build configuration.

Before enabling call buttons in the app, test with two authorized ChatVerse accounts: each should receive a token for the same room; a non-participant, logged-out user, invalid call ID, or ended call must be rejected. The current Android call UI is not yet wired to this endpoint.
