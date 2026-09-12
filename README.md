Synka v2.0.4

Build fix: de-duplicate NewPipeExtractor artifact paths during the Android 11 compatibility patch.

# Synka 2.0.1

Watch together, closer than ever.

## What's new

- Messenger-style room chat: your messages on the right, friends on the left.
- Friend typing indicators with automatic timeout.
- Message timestamps and delivery check marks.
- Twenty distinct anime-inspired built-in profile avatars plus custom image upload.
- Random avatar selection.
- Smart Watch From Link loading state and friendlier error messages.
- Native Media3 playback for supported public media.
- Direct MP4/WebM/M3U8/MPD and other common media URLs.
- YouTube resolution remains serverless and Android 11 compatible.
- Picture-in-picture playback on Android 8+.
- Player control lock with a quick unlock button.
- Playback speed, subtitle/audio tracks, captions and fullscreen controls.
- Room member status display (online, playing, paused).
- Reconnect/catch-up support.
- Recent room shortcuts.
- Android Share -> Synka for public video links.
- Built-in diagnostics with copy-to-clipboard.
- No screen capture and no DRM/paywall/access-control bypass.

## Synchronization safety

The playback synchronization core from the known-good Synka 1.7.1 baseline is intentionally preserved. New UI, chat, diagnostics, profiles and online-media functionality are layered around it.

## Build

The GitHub Actions workflow uses Java 17 and Gradle 8.9.


## Synka 2.0.2

Build/runtime compatibility fixes and temporary Developer Settings for Dual Space testing. The synchronization core remains based on the v1.7.1 stable path.
