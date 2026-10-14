package com.msegal.tunegame.room

import com.msegal.tunegame.game.Song
import kotlin.test.*

class PrivateRoomsTest {
    private val songs = listOf(Song("A", "Album A", "Artist A"), Song("B", "Album B", "Artist B"), Song("C", "Album C", "Artist C"), Song("D", "Album D", "Artist D"))
    private var now = 0L
    private fun rooms() = PrivateRooms(songs, clock = { now })

    @Test fun `room members see identical questions but no answer before reveal`() {
        val rooms = rooms()
        val room = rooms.create("host", "Host")
        rooms.join(room.code, "guest", "Guest")
        val question = rooms.start(room.code, "host")
        assertEquals(question.question, rooms.state(room.code, "guest").question)
        assertNull(question.correctAnswer)
        assertEquals(3, question.players.first().lives)
        assertFailsWith<RoomFailure> { rooms.state(room.code, "outsider") }
        assertFailsWith<RoomFailure> { rooms.next(room.code, "guest", question.round) }
    }

    @Test fun `answers are applied once and revealed only after all players answer`() {
        val rooms = rooms()
        val lobby = rooms.create("host", "Host")
        rooms.join(lobby.code, "guest", "Guest")
        val round = rooms.start(lobby.code, "host")
        val first = rooms.answer(lobby.code, "host", round.round, "incorrect")
        assertEquals("QUESTION", first.phase)
        assertNull(first.correctAnswer)
        val retry = rooms.answer(lobby.code, "host", round.round, "incorrect")
        assertEquals(first.players.first().lives, retry.players.first().lives)
        val revealed = rooms.answer(lobby.code, "guest", round.round, "incorrect")
        assertEquals("RESULTS", revealed.phase)
        assertNotNull(revealed.correctAnswer)
        assertEquals(listOf(2, 2), revealed.players.map { it.lives })
        val next = rooms.next(lobby.code, "host", round.round)
        assertEquals(round.round + 1, next.round)
        assertFailsWith<RoomFailure> { rooms.answer(lobby.code, "guest", round.round, "incorrect") }
    }

    @Test fun `server timeout costs one life and three missed rounds finish the game`() {
        val rooms = rooms()
        val lobby = rooms.create("host", "Host")
        rooms.join(lobby.code, "guest", "Guest")
        rooms.start(lobby.code, "host")
        repeat(3) { i ->
            now += 30_000_000_000L
            val state = rooms.state(lobby.code, "guest")
            assertEquals(2 - i, state.players.first().lives)
            assertEquals(if (i == 2) "FINISHED" else "RESULTS", state.phase)
            if (i < 2) rooms.next(lobby.code, "host", state.round)
        }
    }

    @Test fun `only host starts and membership freezes after start`() {
        val rooms = rooms()
        val lobby = rooms.create("host", "Host")
        assertFailsWith<RoomFailure> { rooms.start(lobby.code, "host") }
        rooms.join(lobby.code, "guest", "Guest")
        assertFailsWith<RoomFailure> { rooms.start(lobby.code, "guest") }
        rooms.start(lobby.code, "host")
        assertFailsWith<RoomFailure> { rooms.join(lobby.code, "third", "Third") }
        assertEquals(2, rooms.join(lobby.code, "guest", "Guest").players.size)
    }

    @Test fun `leaving transfers host and invalid names and capacity are rejected`() {
        val rooms = rooms()
        assertFailsWith<RoomFailure> { rooms.create("host", " ") }
        val lobby = rooms.create("host", "Host")
        (1..7).forEach { rooms.join(lobby.code, "$it", "Player $it") }
        assertFailsWith<RoomFailure> { rooms.join(lobby.code, "overflow", "Extra") }
        rooms.leave(lobby.code, "host")
        val state = rooms.state(lobby.code, "1")
        assertEquals(state.you, state.host)
        assertEquals(7, state.players.size)
    }

    @Test fun `correct answers use existing time and streak scoring`() {
        val rooms = rooms()
        val lobby = rooms.create("host", "Host")
        rooms.join(lobby.code, "guest", "Guest")
        var view = rooms.start(lobby.code, "host")
        repeat(2) { index ->
            val question = requireNotNull(view.question).text
            val correct = songs.firstNotNullOf { song ->
                when (question) {
                    "Name the album that ${song.song} is on?" -> song.album
                    "Name the artist who wrote ${song.song}?" -> song.artist
                    "Name the artist who made ${song.album}?" -> song.artist
                    else -> null
                }
            }
            now += 5_000_000_000L
            view = rooms.answer(lobby.code, "host", view.round, correct)
            val player = view.players.first()
            val expected = (0..index).sumOf { com.msegal.tunegame.game.Scoring.calculatePoints(it, 5.0) }
            assertEquals(expected, player.score)
            assertEquals(index + 1, player.streak)
            assertEquals(3, player.lives)
            rooms.answer(lobby.code, "guest", view.round, "wrong")
            if (index == 0) view = rooms.next(lobby.code, "host", view.round)
        }
    }

    @Test fun `expired rooms cannot be revived by joining`() {
        val rooms = rooms()
        val lobby = rooms.create("host", "Host")
        now += 3_600_000_000_001L
        assertFailsWith<RoomFailure> { rooms.join(lobby.code, "guest", "Guest") }
    }
}
