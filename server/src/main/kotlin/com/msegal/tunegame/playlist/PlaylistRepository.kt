package com.msegal.tunegame.playlist

import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

class PlaylistRepository(databaseUrl: String = "jdbc:sqlite:tunegame.db") : AutoCloseable {
    companion object {
        const val MAX_STORED_PLAYLISTS = 50
    }

    private val connection = DriverManager.getConnection(databaseUrl)

    init {
        connection.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
        val columns = connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(playlists)").use { result ->
                buildList { while (result.next()) add(result.getString("name")) }
            }
        }
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                val legacy = columns.isNotEmpty() && "owner_session_id" !in columns
                if (legacy) statement.executeUpdate("ALTER TABLE playlists RENAME TO playlists_legacy")
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS playlists (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        spotify_playlist_id TEXT NOT NULL,
                        owner_session_id TEXT NOT NULL,
                        UNIQUE(owner_session_id, spotify_playlist_id)
                    )
                """.trimIndent())
                if (legacy) {
                    // Keep old rows, but never assign another visitor's private playlist to a new session.
                    statement.executeUpdate("""
                        INSERT INTO playlists SELECT id, name, spotify_playlist_id, '' FROM playlists_legacy
                    """.trimIndent())
                    statement.executeUpdate("DROP TABLE playlists_legacy")
                }
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    @Synchronized
    fun getAll(ownerSessionId: String = ""): List<Playlist> =
        connection.prepareStatement("SELECT * FROM playlists WHERE owner_session_id = ? ORDER BY name").use { statement ->
            statement.setString(1, ownerSessionId)
            statement.executeQuery().use { results ->
                buildList { while (results.next()) add(results.playlist()) }
            }
        }

    @Synchronized
    fun get(id: String, ownerSessionId: String = ""): Playlist? =
        connection.prepareStatement("SELECT * FROM playlists WHERE id = ? AND owner_session_id = ?").use { statement ->
            statement.setString(1, id)
            statement.setString(2, ownerSessionId)
            statement.executeQuery().use { if (it.next()) it.playlist() else null }
        }

    @Synchronized
    fun findBySpotifyPlaylistId(spotifyPlaylistId: String, ownerSessionId: String = ""): Playlist? =
        connection.prepareStatement("SELECT * FROM playlists WHERE spotify_playlist_id = ? AND owner_session_id = ?").use { statement ->
            statement.setString(1, spotifyPlaylistId)
            statement.setString(2, ownerSessionId)
            statement.executeQuery().use { if (it.next()) it.playlist() else null }
        }

    @Synchronized
    fun create(name: String, spotifyPlaylistId: String, ownerSessionId: String = ""): Playlist {
        findBySpotifyPlaylistId(spotifyPlaylistId, ownerSessionId)?.let { return it }
        check(getAll(ownerSessionId).size < MAX_STORED_PLAYLISTS) { "Maximum number of playlists reached" }
        val playlist = Playlist(UUID.randomUUID().toString(), name, spotifyPlaylistId)
        connection.prepareStatement("INSERT INTO playlists VALUES (?, ?, ?, ?)").use { statement ->
            statement.setString(1, playlist.id)
            statement.setString(2, playlist.name)
            statement.setString(3, playlist.spotifyPlaylistId)
            statement.setString(4, ownerSessionId)
            statement.executeUpdate()
        }
        return playlist
    }

    @Synchronized
    fun delete(id: String, ownerSessionId: String = ""): Boolean =
        connection.prepareStatement("DELETE FROM playlists WHERE id = ? AND owner_session_id = ?").use { statement ->
            statement.setString(1, id)
            statement.setString(2, ownerSessionId)
            statement.executeUpdate() > 0
        }

    private fun ResultSet.playlist() = Playlist(getString("id"), getString("name"), getString("spotify_playlist_id"))

    @Synchronized
    override fun close() = connection.close()
}
