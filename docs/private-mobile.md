# Private mobile TuneGame

Scope confirmed: retain the existing website look and game logic; add Android and iOS apps and private rooms for friends, using only the built-in quiz.

## Implementation

- `private-rooms`: session-protected room lobby, shared questions, per-player existing GameEngine scores/streaks/three lives, existing 30-second deadline, results after everyone answers or time expires. Host advances to the next question; eliminated players can watch. No ten-question rule or new scoring system.
- `room-interface`: reuse the existing HTML screens and CSS at `/rooms/`, with a small separate room controller. Existing solo `/play/` remains available.
- `mobile-shells`: Android WebView and iOS WKWebView load `/rooms/` from a configured server. No duplicate game engine or redesign. These require connectivity to the same server; they are not offline native rewrites.

Build order: room service → shared room interface → Android and iOS shells.

## Acceptance

Two independent sessions can create/join a private room, see the same question, answer once per round, see correct answers only after everyone finishes or time expires, and compare server-calculated scores. Only the host can start/advance; outsiders cannot read room state. Room codes use cryptographic randomness. Joining closes when play starts. Rooms expire and memory is bounded. A browser refresh retains membership through the existing signed cookie. No Spotify calls occur in room games.

The room UI retains TuneGame's styling and supports typed and multiple-choice answers. Use textContent for all player/server content. Poll while visible with a single request in flight; retain the displayed round on connection errors and support retry. Timer is server-authoritative.

## Verification

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :server:test :shared:jvmTest :shared:jsBrowserTest :server:installDist`
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew -p mobile/android :app:assembleDebug`
- iOS: build the Xcode project for a simulator, then sign to run on registered personal devices. Full Xcode is not installed on this machine, so iOS build/device verification is currently blocked.
- Exercise two independent browser sessions against the installed server, including timeout, duplicate submission, non-host control, disconnect/reload, and final scoreboard.

## Boundaries

No App Store/Play Store publishing, Spotify integration in rooms, accounts, database migration, or website redesign. Preserve existing tests and game logic. Keep server URL and signing credentials out of version control. One server process owns temporary rooms; restart ends rooms. Friends need network access to that server. HTTPS is required outside local development.

Private installation does not itself exempt a Spotify app from Spotify's Developer Policy. This version uses the built-in text quiz and contains no streamed audio.
