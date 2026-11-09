# ChatVerse

**Beyond Messaging** — an Android communication app built with Kotlin, Jetpack Compose, and Supabase.

> **Project status: active development / not yet ready for public beta.** This README describes the current repository state and does not mark planned features as complete.

## Current implementation

- Android app scaffold using Kotlin, Jetpack Compose, Material 3, and OkHttp.
- Supabase Auth registration and login using email + password, with a username captured at registration.
- Refresh-token persistence and session restoration.
- Profile display/editing for username, display name, and bio.
- Private conversations, group conversations, and ChatVerse Global room.
- Message sending, replies, edit/delete flows, typing status, read receipts/reactions where supported by the current schema.
- Private chat media upload for JPEG, PNG, WebP, MP4, and PDF files, with signed-link opening.
- Supabase Storage buckets for public avatars and private chat media.
- Row Level Security policies on the current public application tables.
- GitHub Actions workflow for unit tests, lint, debug APK creation, and Android emulator launch smoke test.

## Important known limitations

The following are **not implemented or not yet verified**:

- Username-only registration and login. The current authentication form still asks for an email address.
- Full Supabase Realtime client subscriptions. The current chat and inbox screens use periodic REST polling; only the messages table is currently included in the Realtime publication.
- Five-tab navigation (Chats, Updates, Communities, Calls, Settings).
- Stories, Close Friends, story privacy and expiry.
- Communities and announcement channels.
- Audio/video calls and WebRTC signaling/STUN/TURN infrastructure.
- Push notifications.
- Official developer account provisioning and verified backend badge for ZakssX.
- Secure encrypted local token storage; session tokens currently use app preferences and require a dedicated secure-storage hardening pass.
- Full integration, multi-account authorization, and real-device regression testing.

Do not treat a visible screen or button as proof that a feature is fully functional.

## Backend

The Supabase project currently contains the core messaging tables:

- profiles
- conversations
- conversation_members
- messages
- message_receipts
- message_reactions
- conversation_typing
- user_blocks
- user_reports

RLS is enabled on the current public tables. Storage includes:

- avatars — public profile images, limited to 5 MiB and supported image MIME types.
- chat-media — private conversation media, limited to 20 MiB and supported image/video/PDF MIME types.

Schema changes must be committed as reviewable migrations. Never put a Supabase service-role or secret key in the Android client. The Android app uses only the project's publishable key.

## Build and verification

GitHub Actions is configured in .github/workflows/android.yml.

The workflow runs:

1. gradle :app:testDebugUnitTest :app:lintDebug
2. gradle :app:assembleDebug
3. Uploads the ChatVerse-debug artifact.
4. Installs and launches the APK in an Android emulator.
5. Uploads an emulator screenshot if the smoke test succeeds.

A successful Gradle build does not by itself prove the app is ready for beta. The emulator smoke test and real-device/backend scenarios must also pass.

Latest known run at the time of this README update: run #77 passed unit tests, lint, and debug APK generation, but its emulator smoke test failed because the hosted emulator timed out while booting. The workflow boot allowance has been increased for the next run.

## Local requirements

- JDK 17
- Android SDK / Android Studio
- Android SDK Platform 35
- Gradle 8.9 (CI installs Gradle 8.9)

Open the repository in Android Studio and build the app debug variant.

## Release policy

- Debug artifacts are for testing only.
- No Play Store publication or public beta release should happen until feature, security, and device testing has been reviewed.
- Keep secrets out of GitHub source files and client builds.