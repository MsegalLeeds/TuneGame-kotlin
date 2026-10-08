package com.msegal.tunegame.game

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameSessionManager(
    private val songs: List<Song>
) {
    private val sessions = ConcurrentHashMap<String, GameSession>()

    fun createGame(): String {
        val gameId = UUID.randomUUID().toString()

        val engine = GameEngine(songs)
        engine.newGame()

        sessions[gameId] = GameSession(
            engine = engine
        )

        return gameId
    }

    fun getGame(gameId: String): GameSession? {
        return sessions[gameId]
    }

    fun removeGame(gameId: String) {
        sessions.remove(gameId)
    }
}