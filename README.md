# TuneGame

TuneGame is a music-quiz application. The Kotlin server runs the game API, manages game and user sessions, stores playlists and scores, and can connect to Spotify for playlist-based games and playback. The repository also contains a Kotlin Multiplatform shared client and a browser UI.

## Architecture

- **`server/`** is a Kotlin/JVM application built with Ktor and Netty. Its entry point is `com.msegal.tunegame.ApplicationKt`; it loads the default song list from `server/src/main/resources/music.csv` and serves HTTP on port `8080`.
- **Game logic** (`server/.../game/`) manages rounds, answer checking, scoring, streaks, and lives. Game state is held in memory, so active games do not survive a server restart.
- **Persistence** (`server/.../playlist/`, `score/`, and `spotify/`) uses SQLite. The default database is `tunegame.db` in the working directory.
- **Spotify integration** (`server/.../spotify/`) handles OAuth, token storage, playlist lookups, track search, and playback. The server starts without Spotify credentials, but the current question endpoint also initializes Spotify playback, so configure Spotify to play a full game.
- **`shared/`** contains serializable API models and a Ktor client shared across JVM, JavaScript/browser, and iOS targets. The browser UI is in `shared/src/jsMain/`.

The server returns JSON for API responses and uses an HTTP session cookie (`tunegame_session`) to associate Spotify authorization and tokens with a user. API errors generally use `{"error":"..."}`.

## Run the server

Requirements: Java 21. From the repository root:

```bash
./gradlew :server:run
```

The server listens on `http://localhost:8080`. Check that it is responding:

```bash
curl http://localhost:8080/
```

The response should be `TuneGame server is running!`. The SQLite database file is created in the current working directory as needed.

### Configure Spotify (optional)

Without Spotify credentials, the server and default-song games can run, but Spotify login, playlist operations, and playback are unavailable. To enable Spotify, create an application in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard/) and configure its redirect URI as `http://localhost:8080/spotify/callback`. Then set all three variables before launching the server:

```bash
export SPOTIFY_CLIENT_ID="your-client-id"
export SPOTIFY_CLIENT_SECRET="your-client-secret"
export SPOTIFY_REDIRECT_URI="http://localhost:8080/spotify/callback"
./gradlew :server:run
```

The browser UI expects to run at `http://127.0.0.1:8081` for Spotify's callback redirect and the server's CORS configuration. The UI is a separate browser target; it is not served by the Ktor server.

## API

All paths below are relative to `http://localhost:8080`. Send JSON request bodies with `Content-Type: application/json` where applicable.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/` | Server health response. |
| `GET` | `/session` | Create or retrieve the caller's session ID. |
| `POST` | `/new-game` | Start a default-song game, or a game from a saved Spotify playlist. Optional body: `{"playlistId":"<saved-playlist-id>"}`. |
| `GET` | `/question?gameId=<id>` | Get the next question. |
| `POST` | `/answer` | Submit an answer. Body: `{"gameId":"<id>","answer":"<answer>"}`. |
| `GET` | `/playlist` | List saved Spotify playlists. |
| `POST` | `/playlist` | Save a Spotify playlist. Body: `{"name":"<name>","spotifyPlaylistId":"<spotify-id>"}`. The server retrieves the playlist name from Spotify. |
| `DELETE` | `/playlist/{id}` | Delete a saved playlist. |
| `GET` | `/scores` | List saved high scores. |
| `POST` | `/scores` | Submit a completed game's score. Body: `{"playerName":"<name>","gameId":"<id>"}`. |
| `GET` | `/spotify/login` | Begin Spotify OAuth authorization. |
| `GET` | `/spotify/callback` | Spotify OAuth callback; normally called by Spotify. |
| `GET` | `/spotify/status` | Check whether the current session is connected to Spotify. |
| `POST` | `/spotify/logout` | Disconnect Spotify for the current session. |
| `GET` | `/spotify/search?song=<title>&artist=<name>` | Search Spotify for a track. |
| `POST` | `/spotify/play?song=<title>&artist=<name>` | Search for and play a track, starting at 30 seconds. |
| `POST` | `/spotify/pause` | Pause Spotify playback. |

Spotify authorization and Spotify-backed operations use the same session cookie; clients should retain and send cookies between requests so Spotify remains associated with the same user. A game score can be submitted only once the game is over.

Example game flow:

```bash
curl -X POST http://localhost:8080/new-game \
  -H 'Content-Type: application/json' \
  -d '{}'

curl 'http://localhost:8080/question?gameId=<game-id>'

curl -X POST http://localhost:8080/answer \
  -H 'Content-Type: application/json' \
  -d '{"gameId":"<game-id>","answer":"<your-answer>"}'
```

## Tests

Run the server and shared module tests with:

```bash
./gradlew :server:test :shared:allTests
```
