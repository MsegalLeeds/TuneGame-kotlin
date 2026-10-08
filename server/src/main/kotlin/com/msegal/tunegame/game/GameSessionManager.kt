package com.msegal.tunegame.game

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameSessionManager(
    private val songs: List<Song>
) {
    private val sessions = ConcurrentHashMap<String, GameSession>()

    fun createGame(
        gameSongs: List<Song> = songs
    ): String {

        require(gameSongs.isNotEmpty()) {
            "Game requires at least one song"
        }

        val gameId =
            UUID.randomUUID().toString()

        val engine =
            GameEngine(gameSongs)

        engine.newGame()

        sessions[gameId] =
            GameSession(engine)

        return gameId
    }

    fun getGame(gameId: String): GameSession? {
        return sessions[gameId]
    }

    fun removeGame(gameId: String) {
        sessions.remove(gameId)
    }
}