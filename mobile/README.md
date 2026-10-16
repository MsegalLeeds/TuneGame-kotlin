# Private TuneGame apps

These are small installable shells around the existing TuneGame web interface. Android uses WebView; iOS uses SwiftUI and WKWebView. The same server supplies both apps, so quiz logic and styling stay in one place. Internet access to that server is required; this is not an offline implementation.

## Play

1. Run the updated TuneGame server. For phones, configure an HTTPS origin reachable by all friends, with `PUBLIC_URL` and a stable `SESSION_SIGNING_KEY` of at least 32 bytes. Use one server instance for rooms.
2. Open the app and enter that origin (for example, `https://your-server.example`, without `/play` or `/rooms`).
3. Enter your name and create a room. Share the eight-character code privately. Friends enter their names and join using the code.
4. The host starts once at least two people have joined. Everyone sees the same question. Existing three-life, streak, speed-scoring and 30-second rules apply. Host advances after the answer reveal. Eliminated players can watch; the game ends when everyone is out of lives.

The browser can use the same feature at `/rooms/`. Existing solo play stays at `/play/`. Room games use the built-in text quiz only: no Spotify connection or audio playback. Private installation does not exempt an app from Spotify's Developer Policy.

Rooms are temporary: restart ends them, one hour without member activity expires them, and there is an eight-hour absolute limit. Up to eight players may join before the host starts. The room code admits new players; keep it private. Refreshing the page preserves membership using the existing signed session cookie. Clearing app data/cookies or losing a session after an app restart may require creating a new room. Leaving transfers hosting to another player. Switching servers does not automatically leave the previous room; use Leave room first.

## Spotify in the main game

Choose **Main game**, then **connect** beside the Spotify status. Android opens the system browser; iOS opens a Safari sheet. Complete Spotify sign-in, then return to TuneGame on Android or tap **Done** on iOS. The game refreshes and loads playlists for the connected account. Closing sign-in early leaves Spotify disconnected and you can try again. Connecting restarts the main-game screen.

Deploy the updated server together with these app versions. Keep `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` on the server and register its exact HTTPS `/spotify/callback` URL as `SPOTIFY_REDIRECT_URI` in the Spotify dashboard. The origin entered in the app must match `PUBLIC_URL` and the callback origin, so the browser receives its own session cookie on return. A Spotify account and an available playback device are still required for music playback; this change does not add an on-device player.

The app creates a single-use browser handoff that expires after five minutes. Authorization state expires after ten minutes and is bound to the browser session that starts sign-in. The server saves Spotify tokens against the original app session; it never copies game cookies or Spotify tokens into the browser URL. Handoffs and pending states are in memory, so sign-in must be retried after a server restart. Avoid recording query strings on the handoff and callback paths in proxy access logs.

Automated tests cover separate signed app/browser cookies, session isolation, wrong-browser callbacks, ticket and callback replay, expiry, denial, token exchange failure, and ordinary browser sign-in. Android and iOS simulator builds pass. Live Spotify authorization and device playback still require an end-to-end check with the deployed HTTPS server.

## Android

From the repository root, with Java 21 and Android SDK 36 installed:

```sh
./gradlew -p mobile/android :app:assembleDebug
```

Install `mobile/android/app/build/outputs/apk/debug/app-debug.apk` on your own Android device, or use Android Studio to run `mobile/android` on an emulator. Debug builds accept `http://10.0.2.2:8080` for the Android emulator's connection to the host server. Release builds require HTTPS. The debug APK is for private testing, not store submission.

## iOS

Open `mobile/ios/TuneGame.xcodeproj` in full Xcode. Select the TuneGame target, choose your signing team and a unique bundle identifier, then run on your iPhone or an iOS 17+ simulator. A simulator debug build can connect to `http://localhost:8080`; physical devices should use the shared HTTPS server.

```sh
xcodebuild -project mobile/ios/TuneGame.xcodeproj -scheme TuneGame -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Device installation requires Apple signing/provisioning. This repository does not include signing credentials, a signed IPA, or an App Store/TestFlight deployment. iOS source parsing and project/plist validation have passed, but compilation and real-device testing remain unverified because this machine has Command Line Tools rather than full Xcode.

## Verification status

Server room tests cover separate cookie sessions, host authorization, hidden answers, shared questions, exactly-once answer effects, stale rounds, existing speed/streak scoring, timeouts, lives, capacity, host transfer and expiry. The Android debug APK builds. Browser checks cover room creation and joining; finish device testing before relying on this for a game night.
