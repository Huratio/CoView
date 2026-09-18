# Synka 2.0.1 feature pass

## Chat
- Messenger-style left/right message bubbles
- Your messages on the right; friends on the left
- Avatar shown beside each message
- Timestamps and delivery check marks
- Typing indicator with automatic timeout
- Long-press message reactions, synchronized to the room
- Emoji picker

## Profiles
- 20 distinct anime-inspired built-in avatars
- Mix of cute characters, warriors, masks, armor, ribbons, crowns and themed designs
- Random avatar button
- Custom image upload

## Player
- Existing playback speed, subtitle, audio and fullscreen controls
- Picture-in-picture on Android 8+
- Player control lock with an on-player unlock button

## Room
- Member status labels
- Recent room shortcuts
- Android Share -> Synka for shared public URLs
- Existing reconnect/catch-up behavior retained

## Online media
- Native Media3 playback
- Direct media URLs
- Public YouTube resolution using the existing Android-11-patched NewPipe path
- Smart resolving overlay
- Human-readable errors
- Failed links are not announced to the room

## Diagnostics
- Android/API information
- Media3 state
- connection state
- current room/source
- resolver state
- player state
- room member count
- chat count
- copy diagnostics button

## Safety boundary
Protected/DRM/paywalled/access-controlled media is not bypassed.

## Sync safety
The v1.7.1 `initPlayer()`, `sendSync()` and `applySync()` implementations are preserved byte-for-byte.
