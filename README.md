# ChatVerse

**Beyond Messaging** — an Android messaging app built with Kotlin, Jetpack Compose, and Supabase.

## Project status

- [x] Repository initialized
- [x] Supabase database and storage foundation prepared
- [ ] Android application scaffold
- [ ] Authentication integration
- [ ] Realtime personal and group chat
- [ ] Media sharing, profiles, and notifications

## Stack

- Kotlin + Jetpack Compose
- Material 3
- Supabase Auth, Postgres, Realtime, and Storage
- GitHub Actions for Android APK builds

## Security

The Android client must use only the Supabase publishable key. Never place a Supabase `service_role` or secret key in the app. Database access is protected by Row Level Security (RLS).

## Build

The Android debug APK will be built by the GitHub Actions workflow in `.github/workflows/android.yml`.
