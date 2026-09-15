# Synka Android

Synka is a native Android watch-together client built around Media3 playback and a single Supabase Realtime room service.

## Current architecture

`MainActivity` owns the Android UI, Media3 player, YouTube WebView bridge, subtitles, chat UI, and user actions.

`SyncService` is the only realtime transport. It owns the WebSocket connection, room join/presence, broadcasts, reconnects, media-session identity, and event forwarding back to `MainActivity`.

The synchronization architecture is intentionally unchanged. The repair work hardens the existing path rather than replacing it.

## Synchronization guarantees

The current sync path includes:

- play/pause synchronization
- seek synchronization
- playback-speed synchronization
- periodic native playback heartbeats
- Web/YouTube playback synchronization
- request-id based state catch-up
- per-sender sequence ordering
- duplicate event suppression
- remote-apply guards
- logical media-session revisions and per-session ordering to prevent stale events from crossing media changes
- lifecycle restoration of a connected room, including persisted local media selection when access remains available
- room presence, typing, chat, and reactions

Playback position compensation is speed-aware, so a delayed sync packet advances according to the sender's playback rate instead of assuming 1x.

## Connection and recovery

The activity can ask the running `SyncService` for its current connection state after lifecycle recreation. This prevents the UI from becoming stuck on the join screen when the service remained connected while the Activity was recreated.

Short-lived user actions such as chat/reactions are retained only for a brief reconnect window and stale queued actions are discarded. The latest media-source announcement can survive a longer reconnect window. Playback heartbeats are never queued as stale events.

Realtime connections require a valid HTTPS Supabase origin. Cleartext WebSocket connections are intentionally rejected, and the client rejects server URLs with query fragments, user-info, or unexpected paths.

## Media sessions

Each selected media source gets a media-session identifier. Native and Web playback state carries that identifier. A sync event from a previous media selection is therefore ignored instead of being applied to a newer video.

The identifier is kept in memory for the active room/session. It is deliberately not persisted across a full process restart; a new session is established through the normal room state catch-up path.

## UI and product scope

The existing dark, minimalist UI structure is preserved.

Removed legacy/testing-only systems:

- second in-app test client
- developer settings
- player Picture-in-Picture
- player lock/unlock mode
- legacy color-wheel/accent editor
- room theme presets and custom gradients
- legacy green logo asset
- duplicate SVG icon-pack copy
- unused legacy code paths identified during the audit

The following product features remain intentionally deferred:

- server-side video upload
- Browse/Watch Movies library
- audio calling

Those controls remain placeholders until their backend/product design is implemented.

## Build

The project intentionally has no Gradle wrapper.

CI uses Gradle 8.9 with Java 17 and builds the Android application with compileSdk/targetSdk 35 and minSdk 23.

## Security boundary

The client uses the Supabase public/anon key and a room topic derived from the room credentials. Never embed a Supabase service-role key in the APK.

Application backup is disabled because reconnect credentials are kept locally. The room password is stored encrypted with an Android Keystore AES-GCM key.

Production deployment should still use authenticated/private Realtime channels and appropriate server-side authorization before exposing rooms to untrusted users.
