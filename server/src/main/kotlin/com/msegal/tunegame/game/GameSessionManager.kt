package com.msegal.tunegame.game

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameSessionManager(
    private val songs: List<Song>,
    private val clock: () -> Long =
        System::currentTimeMillis,
    private val sessionTtlMs: Long =
        DEFAULT_SESSION_TTL_MS
) {

    companion object {
        const val DEFAULT_SESSION_TTL_MS =
            60 * 60 * 1000L
    }

    private data class SessionEntry(
        val session: GameSession,
        val createdAt: Long
    )

    private val sessions =
        ConcurrentHashMap<String, SessionEntry>()

    fun createGame(
        gameSongs: List<Song> = songs
    ): String {

        require(gameSongs.isNotEmpty()) {
            "Game requires at least one song"
        }

        removeExpiredGames()

        val gameId =
            UUID.randomUUID().toString()

        val engine =
            GameEngine(gameSongs)

        engine.newGame()

        sessions[gameId] =
            SessionEntry(
                session =
                    GameSession(engine),
                createdAt =
                    clock()
            )

        return gameId
    }

    fun getGame(
        gameId: String
    ): GameSession? {

        val entry =
            sessions[gameId]
                ?: return null

        if (
            clock() - entry.createdAt >=
            sessionTtlMs
        ) {
            sessions.remove(
                gameId,
                entry
            )

            return null
        }

        return entry.session
    }

    fun removeGame(
        gameId: String
    ) {
        sessions.remove(gameId)
    }

    fun removeExpiredGames() {

        val now =
            clock()

        sessions.entries.removeIf {
            now - it.value.createdAt >=
                    sessionTtlMs
        }
    }

    fun sessionCount(): Int =
        sessions.size
}