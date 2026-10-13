package com.msegal.tunegame.playlist

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.*

class PlaylistOwnershipTest {
    @Test
    fun `migration preserves legacy playlists without exposing them to new visitors`() {
        val databaseFile = Files.createTempFile("tunegame-legacy-", ".db")
        val databaseUrl = "jdbc:sqlite:$databaseFile"
        try {
            DriverManager.getConnection(databaseUrl).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate("""
                        CREATE TABLE playlists (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            spotify_playlist_id TEXT NOT NULL UNIQUE
                        )
                    """.trimIndent())
                    statement.executeUpdate("INSERT INTO playlists VALUES ('old-id', 'Old playlist', 'spotify123')")
                }
            }

            val legacy = Playlist("old-id", "Old playlist", "spotify123")
            val owned = PlaylistRepository(databaseUrl).use { repository ->
                assertEquals(legacy, repository.get("old-id"))
                assertEquals(listOf(legacy), repository.getAll())
                assertTrue(repository.getAll("visitor").isEmpty())
                assertNull(repository.get("old-id", "visitor"))
                assertNull(repository.findBySpotifyPlaylistId("spotify123", "visitor"))
                assertFalse(repository.delete("old-id", "visitor"))
                repository.create("My playlist", "spotify123", "visitor")
            }

            // Reopening performs the same schema check and must preserve both owners' rows.
            repeat(2) {
                PlaylistRepository(databaseUrl).use { repository ->
                    assertEquals(listOf(legacy), repository.getAll())
                    assertEquals(listOf(owned), repository.getAll("visitor"))
                    assertNull(repository.get(legacy.id, "visitor"))
                    assertEquals(owned, repository.create("Renamed", "spotify123", "visitor"))
                }
            }
        } finally {
            Files.deleteIfExists(databaseFile)
        }
    }

    @Test
    fun `two owners can save the same Spotify playlist and delete independently`() {
        PlaylistRepository("jdbc:sqlite::memory:").use { repository ->
            val first = repository.create("First owner's playlist", "shared-spotify-id", "first")
            val second = repository.create("Second owner's playlist", "shared-spotify-id", "second")

            assertNotEquals(first.id, second.id)
            assertEquals(listOf(first), repository.getAll("first"))
            assertEquals(listOf(second), repository.getAll("second"))
            assertFalse(repository.delete(first.id, "second"))
            assertTrue(repository.delete(first.id, "first"))
            assertNull(repository.get(first.id, "first"))
            assertEquals(second, repository.get(second.id, "second"))
            assertEquals(second, repository.findBySpotifyPlaylistId("shared-spotify-id", "second"))
            assertTrue(repository.delete(second.id, "second"))
        }
    }
}
