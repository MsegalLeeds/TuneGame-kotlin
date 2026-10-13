# TuneGame

A Kotlin music quiz with a Ktor server, a Kotlin/JS browser interface, and shared JVM/JS/iOS client code.

## Run and build

Use Java 21 and the checked-in Gradle wrapper:

```sh
./gradlew :server:run
```

Open <http://127.0.0.1:8080/play/>. The server serves both the browser bundle and API. The built-in quiz works without Spotify credentials. The root `/` endpoint remains a plain-text health check.

To produce a standalone distribution:

```sh
./gradlew :server:test :shared:jvmTest :shared:jsBrowserTest :server:installDist :server:distZip
python3 scripts/smoke_release.py
```

Browser tests require Chrome or Chromium; set `CHROME_BIN` if it is not detected. The ZIP is in `server/build/distributions/`. Extract it and run `bin/server` with Java 21. The shared iOS targets are libraries, not a shipped iOS app; the web release checks cover JVM and JavaScript.

## Your Spotify playlists

When Spotify is connected, the start screen automatically loads **Your Spotify playlists**. Choose one to save it for your browser session and select it as the game's song source. The picker supports pagination, refresh, empty accounts, and retry after a failed request. Disconnecting hides and clears its choices. Pasting a playlist URL still works.

Only the connected visitor's access token is used. Tokens stay on the server. The list uses Spotify's `GET /v1/me/playlists` endpoint; the existing OAuth scopes include `playlist-read-private` and `playlist-read-collaborative`. Spotify may list followed playlists that its current API does not permit this app to read for gameplay; the game reports failures when loading those playlists. A playlist must have at least four songs.

### Public launch blocker

Spotify's [Developer Policy](https://developer.spotify.com/policy) prohibits games, including trivia quizzes. This repository's Spotify integration must not be described as approved for a public quiz launch. Resolve this with Spotify or use an appropriately licensed source before publishing Spotify-powered gameplay. No live Spotify credentials or playback were used in automated verification; the integration is tested against simulated API responses.

For authorized integration testing, configure an application in the Spotify developer dashboard and set:

```sh
export SPOTIFY_CLIENT_ID='your-client-id'
export SPOTIFY_CLIENT_SECRET='your-client-secret'
export SPOTIFY_REDIRECT_URI='http://127.0.0.1:8080/spotify/callback'
```

The registered callback must exactly match `SPOTIFY_REDIRECT_URI`. The callback returns to `${PUBLIC_URL}/play/`. Spotify playback needs a supported account and active Spotify device. Without Spotify, play the text quiz using the built-in song list.

## Public deployment configuration

Set these environment variables in your host's secret/configuration manager:

| Variable | Value |
| --- | --- |
| `PUBLIC_URL` | HTTPS origin, e.g. `https://quiz.example.com`, with no trailing slash or path. |
| `SESSION_SIGNING_KEY` | Stable, randomly generated secret of at least 32 bytes. Required for a public origin. |
| `PORT` | Listening port, default `8080`. |
| `DATABASE_URL` | SQLite JDBC URL on persistent storage, e.g. `jdbc:sqlite:/data/tunegame.db`. |

Generate a signing secret with `openssl rand -hex 32`, save it securely, and retain it across restarts. Local HTTP on `127.0.0.1` or `localhost` can use a temporary key; restarting then invalidates cookies.

Run **one server instance** behind an HTTPS reverse proxy. Keep the application port private. Configure the proxy to limit request bodies to 16 KiB, rate-limit abusive clients, and compress JavaScript responses. The application also limits traffic to 300 requests/minute per socket peer. It deliberately does not trust incoming forwarded-IP headers, so visitors behind one proxy share that application limit. Configure capacity at the proxy before increasing traffic.

Use restricted permissions for the SQLite file and backups; the database holds Spotify refresh tokens. Games and OAuth state are in memory and do not survive restarts. Playlists belong to a signed browser session, not a permanent account; losing that cookie loses access to the saved list. The leaderboard is public, so use a nickname rather than personal information.

### Migration and rollback

Before upgrading an existing deployment, stop the server and back up its SQLite database. Startup migrates playlists to per-session ownership in a transaction. Existing unowned playlists are preserved in the database but hidden from new visitors; users can import them again. This avoids exposing another user's saved playlist.

To roll back, stop the new server, restore the pre-upgrade database backup, and run the previous distribution. The old version must not run against the migrated schema. Rotating the signing key invalidates all browser sessions.

## API

Clients must retain the `tunegame_session` cookie across calls. Games and saved playlists are private to that cookie; scores are public. Request bodies and responses use JSON.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | Health response. |
| GET | `/play/` | Browser game. |
| GET | `/session` | Create/retrieve the visitor session. |
| POST | `/new-game` | Start a game; optional `{"playlistId":"saved-id"}`. |
| GET | `/question?gameId=…` | Retrieve the next question. |
| POST | `/answer` | Submit `{"gameId":"…","answer":"…"}`. |
| GET/POST | `/playlist` | List/save the visitor's playlists. |
| DELETE | `/playlist/{id}` | Delete the visitor's saved playlist. |
| GET/POST | `/scores` | Public leaderboard / submit a completed game's score once. |
| GET | `/spotify/login` | Start authorization. |
| GET | `/spotify/callback` | Authorization callback. |
| GET | `/spotify/status` | Connection status. |
| GET | `/spotify/playlists` | Connected user's playlists, as `[{"id":"…","name":"…"}]`; 401 disconnected, 503 upstream failure. |
| POST | `/spotify/logout` | Disconnect and remove the stored refresh token. |

## Verification

GitHub Actions runs server tests, shared JVM tests, Chrome browser tests, distribution builds, and the installed-server smoke script. It uploads the distributable ZIP and test reports. Make `Release checks / verify` a required branch check before merging. Live Spotify authorization and playback need a separate authorized account/device check; passing mocks does not establish public-launch approval.
