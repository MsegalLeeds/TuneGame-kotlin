# TuneGame

TuneGame is a Kotlin-based music quiz application with Spotify integration.

The project currently includes:

- a Ktor/Netty backend;
- a Kotlin Multiplatform shared client;
- a browser client;
- Spotify OAuth, playlist access, search, and playback;
- persistent playlists, Spotify tokens, and scores using SQLite;
- session-isolated game state;
- automated tests and GitHub Actions CI.

The long-term goal is to support Web, Android, and iOS clients while sharing API and client-side business logic through Kotlin Multiplatform.

## Architecture

```text
TuneGame-kotlin/
├── server/
│   └── Ktor backend, game engine, persistence and Spotify integration
│
├── shared/
│   ├── commonMain/
│   │   └── Shared API models and client logic
│   ├── jsMain/
│   │   └── Browser application
│   ├── jvmMain/
│   └── iosMain/
│
└── .github/workflows/
    └── CI
```

### Server

`server/` is a Kotlin/JVM application using:

- Ktor
- Netty
- Kotlin serialization
- SQLite
- Spotify Web API integration

The main entry point is:

```text
com.msegal.tunegame.ApplicationKt
```

The server listens on port `8080` by default.

### Game engine

The server manages:

- game creation;
- question generation;
- lives;
- scoring;
- streaks;
- typed and multiple-choice answers;
- question timing;
- clip and full-song playback modes.

Active game sessions are stored in memory.

This means an active game does not survive a server restart.

Games are associated with the user's session, so one browser session cannot access another user's active game.

### Persistence

SQLite is used for persistent application data including:

- saved playlists;
- Spotify authorization data;
- leaderboard scores.

The default development database is:

```text
tunegame.db
```

The database path can be configured using `DATABASE_URL`.

### Spotify

Spotify integration supports:

- OAuth login;
- per-user Spotify sessions;
- saved Spotify playlists;
- track search;
- starting playback;
- pausing playback;
- clip playback starting approximately 30 seconds into the track;
- full-song mode starting at the beginning of the track.

Spotify-backed games require the user to connect their Spotify account.

### Kotlin Multiplatform

`shared/` contains code shared between supported client targets.

Current targets include:

- JVM
- JavaScript/browser
- iOS Arm64
- iOS Simulator Arm64

An Android application module has not yet been added.

## Requirements

For local development:

- Java 21
- Git
- a modern browser
- Spotify Developer credentials for Spotify features

## Running locally

From the repository root:

```bash
./gradlew :server:run
```

The server runs at:

```text
http://localhost:8080
```

Check that it is responding:

```bash
curl http://localhost:8080/
```

You should receive:

```text
TuneGame server is running!
```

## Browser application

The production browser bundle is built from the Kotlin/JS application and packaged into the Ktor server.

Build it with:

```bash
./gradlew :shared:jsBrowserDistribution
```

Running the server then makes the browser application available at:

```text
http://localhost:8080/play/
```

The server redirects:

```text
/play
```

to:

```text
/play/
```

### Browser development mode

During Kotlin/JS development, the browser development server may still run separately on port `8081`.

When running on:

```text
localhost:8081
```

or:

```text
127.0.0.1:8081
```

the browser client automatically connects to the Ktor server on port `8080`.

For production builds, the browser and API use the same origin.

## Spotify configuration

Create an application in the Spotify Developer Dashboard.

For local development, configure the redirect URI as:

```text
http://localhost:8080/spotify/callback
```

Then set:

```bash
export SPOTIFY_CLIENT_ID="your-client-id"
export SPOTIFY_CLIENT_SECRET="your-client-secret"
export SPOTIFY_REDIRECT_URI="http://localhost:8080/spotify/callback"
```

Run:

```bash
./gradlew :server:run
```

Spotify features will then be available through the browser application and API.

Without Spotify credentials, the server can still start and non-Spotify functionality can be developed and tested.

## Application configuration

The server supports environment-based configuration.

### `PORT`

Server port.

Example:

```bash
export PORT=8080
```

### `PUBLIC_URL`

Public base URL of the application.

Local development:

```bash
export PUBLIC_URL="http://localhost:8080"
```

A public deployment should use HTTPS, for example:

```bash
export PUBLIC_URL="https://example.com"
```

### `DATABASE_URL`

SQLite database connection URL.

Example:

```bash
export DATABASE_URL="jdbc:sqlite:tunegame.db"
```

### `SESSION_SIGNING_KEY`

Secret used to sign application session cookies.

Public deployments should provide a stable, private signing key.

Example:

```bash
export SESSION_SIGNING_KEY="replace-with-a-long-random-secret"
```

Do not commit production credentials or signing keys to Git.

## API

API routes are served from the same Ktor application.

For local development, the base URL is:

```text
http://localhost:8080
```

### Game

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/new-game` | Create a new game |
| `GET` | `/question?gameId=<id>` | Request the next question |
| `POST` | `/answer` | Submit an answer |

A saved playlist can be supplied when creating a game.

Example:

```json
{
  "playlistId": "<saved-playlist-id>"
}
```

Question requests can optionally specify a playback position.

For example:

```text
/question?gameId=<id>&positionMs=30000
```

Clip mode uses approximately:

```text
30000 ms
```

Full-song mode uses:

```text
0 ms
```

### Sessions

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/session` | Create or retrieve the user's application session |

Game and Spotify state are associated with this session.

### Spotify

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/spotify/login` | Begin Spotify OAuth |
| `GET` | `/spotify/callback` | Spotify OAuth callback |
| `GET` | `/spotify/status` | Check Spotify connection status |
| `POST` | `/spotify/logout` | Disconnect Spotify |
| `GET` | `/spotify/search` | Search for a track |
| `POST` | `/spotify/play` | Start Spotify playback |
| `POST` | `/spotify/pause` | Pause Spotify playback |

### Playlists

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/playlist` | List the current user's saved playlists |
| `POST` | `/playlist` | Save a Spotify playlist |
| `DELETE` | `/playlist/{id}` | Delete a saved playlist |

Saved playlists are scoped to the application session that created them.

### Scores

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/scores` | Retrieve leaderboard scores |
| `POST` | `/scores` | Submit the result of a completed game |

A game score can only be submitted after the game has finished and can only be submitted once.

## Testing

### Server tests

```bash
./gradlew :server:test
```

### Browser/shared tests

```bash
./gradlew :shared:jsBrowserTest
```

### Production browser build

```bash
./gradlew :shared:jsBrowserDistribution
```

### Server distribution

```bash
./gradlew :server:installDist
```

These are also the main checks run by CI.

The full Kotlin Multiplatform test suite includes iOS targets and therefore requires an appropriately configured macOS/Xcode environment.

## Continuous integration

GitHub Actions runs automatically on pushes and pull requests to `main`.

CI verifies:

- server tests;
- shared browser tests;
- production browser compilation;
- server distribution creation.

## Current status

Implemented:

- Kotlin/Ktor backend
- game engine
- lives, score and streak handling
- multiple-choice answers
- typed answers
- question timing
- Spotify OAuth
- Spotify playback
- clip/full-song playback modes
- saved Spotify playlists
- SQLite persistence
- leaderboard scores
- session-isolated games
- Kotlin/JS browser client
- production browser packaging
- automated server tests
- browser tests
- GitHub Actions CI

Planned:

- deployment of the public web version;
- browser UI/UX improvements;
- Android client;
- iOS client;
- further Kotlin Multiplatform shared client logic.

## Development roadmap

The current priority is to make the Web version production-ready before beginning native mobile clients.

The approximate sequence is:

1. CI and repository cleanup
2. documentation
3. Spotify/session refinements
4. deployment configuration
5. public Web deployment
6. Web UI/UX polish
7. Android client
8. iOS client
9. final project and portfolio polish

## Choose playlists from your Spotify account

After connecting Spotify, the start screen automatically loads **Your Spotify playlists**. Choose one to save and select it as the game source. The picker loads subsequent pages, offers refresh and retry, and hides/clears its choices when Spotify is disconnected. Pasting a playlist URL still works.

`GET /spotify/playlists` returns `[{"id":"…","name":"…"}]` for the connected visitor. It uses the server-held access token and existing `playlist-read-private` and `playlist-read-collaborative` scopes. Disconnected sessions receive 401; upstream failures receive a generic 503. Some followed playlists may be listed but unavailable for gameplay under Spotify's current API restrictions.

### Public-launch blocker

Spotify's [Developer Policy](https://developer.spotify.com/policy) prohibits games, including trivia quizzes. Resolve this with Spotify or use an appropriately licensed source before publicly launching Spotify-powered gameplay. Automated tests use simulated Spotify responses; they do not establish launch approval or verify live OAuth/playback.

### Release verification

```sh
./gradlew :server:test :shared:jvmTest :shared:jsBrowserTest :server:installDist :server:distZip
python3 scripts/smoke_release.py
```

Chrome/Chromium is required for browser tests; set `CHROME_BIN` if needed. CI uploads the release ZIP and test reports. The smoke script starts the installed server with a temporary database, verifies packaged browser assets, visitor isolation, three rounds, game over, and score persistence. It never uses Spotify credentials.

Before upgrading an existing deployment, stop the server and back up SQLite. Legacy playlist migration preserves unowned rows but hides them from new sessions. To roll back to the old schema, restore the backup and previous distribution together. Keep SQLite and its Spotify refresh tokens private, and use one server instance behind an HTTPS reverse proxy with request-size and per-client rate limits. Games do not survive restarts; saved playlists belong to a browser session rather than a permanent account.
