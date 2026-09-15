# Synka architecture

## Transport boundary

`SyncService` remains the single realtime transport.

The flow is:

```text
MainActivity
    │
    │ service intents
    ▼
SyncService
    │
    │ Supabase Realtime WebSocket
    ▼
Room broadcast/presence channel
    │
    │ service events
    ▼
MainActivity
```

The repair work does not introduce a second synchronization engine.

## Native playback

`MainActivity` converts Media3 player events into `ACTION_SYNC` intents.

`SyncService` adds:

- sender identity
- sequence number
- unique event id
- media-session id
- playback speed
- timestamp
- optional state-request id

Incoming events are accepted only when they belong to the current media session.

## Media-session isolation

A new media source creates a new media-session id.

That id is carried by:

- media-source announcements
- state requests
- native sync events
- web sync events
- state responses

A stale event from a previous video is therefore rejected instead of being applied to the new player.

## State catch-up

A joining/recreated Activity creates a request id and asks the service for current room state.

The service announces the request and current media session. A client holding valid state responds with a sync packet carrying the same request id.

The Activity accepts that packet only while waiting for the matching request.

Native responses are deferred until Media3 has a prepared item when necessary. Web responses are deferred until the WebView bridge is ready.

## Ordering and feedback-loop protection

Every outgoing synchronization event receives a monotonically increasing sequence number.

The Activity tracks the newest sequence per sender and keeps a bounded set of event ids to suppress duplicates.

Remote player changes are applied under `applyingRemote` plus a short `remoteGuardUntil` window. This keeps Media3/WebView callbacks from immediately echoing the remote event back to the room.

## Playback-speed compensation

When a synchronization packet reports a playing state, the receiver advances the reported timestamp by:

```text
elapsed real time × playback speed
```

rather than treating every second of network delay as one second of media time.

## Web/YouTube lifecycle

Each WebView media session has a session generation and a per-session bridge nonce.

A callback from an older WebView cannot mutate a newer player session.

The trusted WebView navigation policy only permits the app's local player document and supported YouTube hosts.

## Reconnect behavior

The service uses exponential reconnect delay with a bounded maximum.

Non-idempotent short-lived user actions that are safe to replay, such as chat and reactions, are retained in a small in-memory queue during disconnects. Media-source announcements keep only the newest pending source.

Playback heartbeats are intentionally not replayed because an old position is stale by definition. A delayed manual disconnect cannot stop a newer reconnect because the service cancels the pending stop task when a new connection begins.

## Presence

A fresh presence key is generated after the previous socket is closed. Presence status is updated from the actual local playback state, including remote-applied playback changes.

The Activity can query a still-running service after recreation so the UI can recover the connected room without requiring another manual join.

## UI boundary

The room/player layout remains programmatic so the established spacing and geometry stay stable.

The active theme is limited to the existing dark/light appearance. Legacy room-theme presets, custom gradients, and color-wheel editing are removed.

Fullscreen remains part of the player UI. Picture-in-Picture and player lock mode are intentionally removed.

## Deferred product features

Server upload, Movies/Library browsing, and audio calling remain placeholders. They are outside the current synchronization repair and must not be replaced with fake implementations.

## Local credential handling

The service keeps the room password only for reconnect support. It is encrypted with an Android Keystore AES-GCM key before being persisted. Older plaintext password storage is migrated once when the service next reads the session.
