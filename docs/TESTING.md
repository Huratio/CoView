# Synka regression test plan

Run this against a real Android build. The static audit can catch source/resource mistakes, but synchronization and WebView behavior still require device testing.

## Connection and lifecycle

Join the same room on two devices.

Recreate/rotate the Activity while the room service remains connected. The room screen should recover automatically instead of returning to the join screen.

Temporarily interrupt the network, restore it, and confirm the service reconnects without creating a second room identity. Start a reconnect immediately after pressing Leave/Back and confirm the delayed disconnect cannot terminate the new connection.

## Native playback

Load the same local media on two clients.

Test:

- play
- pause
- seek
- 0.5x / 0.75x / 1x / 1.25x / 1.5x / 2x

Confirm the receiving client changes once and does not echo the remote change back.

Leave playback running at 2x and introduce a small network delay. The receiving position should compensate using the transmitted playback speed.

## Media-session isolation

Switch quickly from Video A to Video B.

Make sure delayed sync packets from Video A do not move Video B.

Repeat with native media followed by YouTube and with YouTube followed by native media.

## State catch-up

Start playback on one client, then join or restore another client.

The new client should obtain the current media session, position, playing state, and speed through the request-id catch-up path.

Repeat while the media/player is still loading.

## YouTube/WebView

Open a supported YouTube URL on two clients.

Test load, retry, play, pause, seek, and speed.

Immediately switch to another YouTube URL and confirm that callbacks from the previous WebView session have no effect.

## Chat and reconnect

Send a chat message while connected.

Briefly disconnect the network and send a chat/reaction during the reconnect window. After reconnect, verify safe queued actions are delivered once.

Typing indicators should not be replayed after reconnect.

## Presence

Join, leave, reconnect, and apply remote playback.

The participant list should show accurate user names, presence identities, profile images, and Playing/Paused status without duplicate self-events.

## Player and UI regression

Check:

- dark mode
- light mode
- fullscreen entry/exit
- volume/mute
- captions/subtitle loading
- subtitle delay
- playback speed
- More menu
- chat overlay
- participant sheet

The established spacing and visual hierarchy should remain intact.

Picture-in-Picture, player lock, developer settings, and legacy room-theme/color-wheel controls should no longer exist.

## Deferred features

Upload Video, Watch Movies/Browse Library, and Audio Call should remain clearly marked as deferred placeholders.
