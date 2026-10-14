package com.msegal.tunegame.room

import com.msegal.tunegame.game.*
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.UUID

@Serializable data class RoomPlayer(val id: String, val name: String, val score: Int, val streak: Int, val lives: Int, val answered: Boolean)
@Serializable data class RoomQuestion(val text: String, val choices: List<String>)
@Serializable data class RoomView(
    val code: String, val you: String, val host: String, val phase: String,
    val round: Int, val remainingMs: Long, val players: List<RoomPlayer>,
    val question: RoomQuestion?, val correctAnswer: String?
)
class RoomFailure(val status: Int, override val message: String) : RuntimeException(message)

/** One process owns temporary rooms. All transitions, including reads that expire a round, are atomic. */
class PrivateRooms(private val songs: List<Song>, private val clock: () -> Long = System::nanoTime) {
    private data class Player(val session: String, val name: String, val engine: GameEngine, val id: String = UUID.randomUUID().toString(), var answered: Boolean = false)
    private class Room(val code: String, val players: MutableList<Player>, val created: Long) {
        var host = players.first().id
        var touched = created
        var phase = "WAITING"
        var round = 0
        var question: Question? = null
        var started = 0L
    }
    private val rooms = mutableMapOf<String, Room>()
    private val random = SecureRandom()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    @Synchronized fun create(session: String, name: String): RoomView {
        validateName(name)
        cleanup()
        if (rooms.size >= 100 || rooms.values.count { r -> r.players.any { it.session == session } } >= 4)
            fail(429, "Too many rooms. Leave an existing room first.")
        var code: String
        do { code = (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") } while (code in rooms)
        val room = Room(code, mutableListOf(Player(session, name.trim(), GameEngine(songs))), clock())
        rooms[code] = room
        return view(room, session)
    }

    @Synchronized fun join(code: String, session: String, name: String): RoomView {
        validateName(name)
        val room = find(code)
        if (room.players.none { it.session == session }) {
            if (room.phase != "WAITING") fail(409, "This game has already started.")
            if (room.players.size >= 8) fail(409, "This room is full.")
            if (room.players.any { it.name.equals(name.trim(), ignoreCase = true) }) fail(409, "Choose a different player name.")
            room.players.add(Player(session, name.trim(), GameEngine(songs)))
        }
        return view(room, session)
    }

    @Synchronized fun state(code: String, session: String): RoomView = view(memberRoom(code, session), session)

    @Synchronized fun start(code: String, session: String): RoomView {
        val room = hostRoom(code, session)
        if (room.phase != "WAITING") return view(room, session) // A retried start must not reset a game.
        if (room.players.size < 2) fail(409, "Wait for a friend to join.")
        beginQuestion(room)
        return view(room, session)
    }

    @Synchronized fun next(code: String, session: String, round: Int): RoomView {
        val room = hostRoom(code, session)
        expireRound(room)
        if (room.round != round) fail(409, "The round has changed. Refresh the room.")
        if (room.phase != "RESULTS") fail(409, "Wait for everyone to finish the question.")
        beginQuestion(room)
        return view(room, session)
    }

    @Synchronized fun answer(code: String, session: String, round: Int, answer: String): RoomView {
        if (answer.length > 500) fail(400, "Answer is too long.")
        val room = memberRoom(code, session)
        expireRound(room)
        if (room.round != round) fail(409, "The round has changed. Refresh the room.")
        val player = room.players.first { it.session == session }
        if (player.answered || player.engine.state.gameOver) return view(room, session)
        if (room.phase != "QUESTION") fail(409, "No question is active.")
        player.engine.checkAnswer(answer, requireNotNull(room.question), elapsed(room))
        player.answered = true
        revealIfFinished(room)
        return view(room, session)
    }

    @Synchronized fun leave(code: String, session: String) {
        val room = memberRoom(code, session)
        room.players.removeAll { it.session == session }
        if (room.players.isEmpty()) { rooms.remove(room.code); return }
        if (room.players.none { it.id == room.host }) room.host = room.players.first().id
        revealIfFinished(room)
    }

    private fun beginQuestion(room: Room) {
        room.question = GameEngine(songs).generateQuestion(QuestionType.entries.random())
        room.round++
        room.started = clock()
        room.phase = "QUESTION"
        room.players.forEach { it.answered = it.engine.state.gameOver }
    }
    private fun elapsed(room: Room) = ((clock() - room.started) / 1_000_000_000.0).coerceAtLeast(0.0)
    private fun expireRound(room: Room) {
        if (room.phase == "QUESTION" && elapsed(room) >= 30.0) {
            room.players.filter { !it.answered && !it.engine.state.gameOver }.forEach {
                it.engine.checkAnswer("", requireNotNull(room.question), 30.0)
                it.answered = true
            }
            revealIfFinished(room)
        }
    }
    private fun revealIfFinished(room: Room) {
        if (room.phase == "QUESTION" && room.players.all { it.answered || it.engine.state.gameOver })
            room.phase = if (room.players.all { it.engine.state.gameOver }) "FINISHED" else "RESULTS"
    }
    private fun view(room: Room, session: String): RoomView {
        expireRound(room)
        room.touched = clock()
        return RoomView(room.code, room.players.first { it.session == session }.id, room.host, room.phase,
            room.round, if (room.phase == "QUESTION") ((30.0 - elapsed(room)) * 1000).toLong().coerceAtLeast(0) else 0,
            room.players.map { RoomPlayer(it.id, it.name, it.engine.state.score, it.engine.state.streak, it.engine.state.lives, it.answered) },
            room.question?.let { RoomQuestion(it.question, it.choices) },
            room.question?.correctAnswer?.takeIf { room.phase == "RESULTS" || room.phase == "FINISHED" })
    }
    private fun find(code: String): Room {
        cleanup()
        return rooms[code.trim().uppercase()] ?: fail(404, "Room not found or expired.")
    }
    private fun memberRoom(code: String, session: String): Room {
        val room = find(code)
        if (room.players.none { it.session == session }) fail(404, "Room not found or expired.")
        return room
    }
    private fun hostRoom(code: String, session: String): Room {
        val room = memberRoom(code, session)
        if (room.players.first { it.session == session }.id != room.host) fail(403, "Only the host can do that.")
        return room
    }
    private fun cleanup() {
        val now = clock()
        rooms.entries.removeIf { (_, r) -> now - r.touched > 3_600_000_000_000L || now - r.created > 28_800_000_000_000L }
    }
    private fun validateName(name: String) {
        if (name.trim().length !in 1..20 || name.any { it.isISOControl() }) fail(400, "Enter a name of 1–20 characters.")
    }
    private fun fail(status: Int, message: String): Nothing = throw RoomFailure(status, message)
}
