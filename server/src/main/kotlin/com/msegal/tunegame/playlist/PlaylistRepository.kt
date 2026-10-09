package com.msegal.tunegame.playlist

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class PlaylistRepository(
    databaseUrl: String = "jdbc:sqlite:tunegame.db"
) {

    companion object {
        const val MAX_STORED_PLAYLISTS = 50
    }

    private val connection: Connection =
        DriverManager.getConnection(databaseUrl)

    init {
        createTable()
    }

    private fun createTable() {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS playlists (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    spotify_playlist_id TEXT NOT NULL UNIQUE
                )
                """.trimIndent()
            )
        }
    }

    fun getAll(): List<Playlist> {
        val playlists =
            mutableListOf<Playlist>()

        connection.prepareStatement(
            """
            SELECT id, name, spotify_playlist_id
            FROM playlists
            ORDER BY name
            """.trimIndent()
        ).use { statement ->

            statement.executeQuery().use { results ->

                while (results.next()) {
                    playlists +=
                        Playlist(
                            id =
                                results.getString("id"),
                            name =
                                results.getString("name"),
                            spotifyPlaylistId =
                                results.getString(
                                    "spotify_playlist_id"
                                )
                        )
                }
            }
        }

        return playlists
    }

    fun get(
        id: String
    ): Playlist? {

        connection.prepareStatement(
            """
            SELECT id, name, spotify_playlist_id
            FROM playlists
            WHERE id = ?
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                id
            )

            statement.executeQuery().use { results ->

                if (!results.next()) {
                    return null
                }

                return Playlist(
                    id =
                        results.getString("id"),
                    name =
                        results.getString("name"),
                    spotifyPlaylistId =
                        results.getString(
                            "spotify_playlist_id"
                        )
                )
            }
        }
    }

    fun findBySpotifyPlaylistId(
        spotifyPlaylistId: String
    ): Playlist? {

        connection.prepareStatement(
            """
            SELECT id, name, spotify_playlist_id
            FROM playlists
            WHERE spotify_playlist_id = ?
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                spotifyPlaylistId
            )

            statement.executeQuery().use { results ->

                if (!results.next()) {
                    return null
                }

                return Playlist(
                    id =
                        results.getString("id"),
                    name =
                        results.getString("name"),
                    spotifyPlaylistId =
                        results.getString(
                            "spotify_playlist_id"
                        )
                )
            }
        }
    }

    fun create(
        name: String,
        spotifyPlaylistId: String
    ): Playlist {

        val existing =
            findBySpotifyPlaylistId(
                spotifyPlaylistId
            )

        if (existing != null) {
            return existing
        }

        if (
            getAll().size >=
            MAX_STORED_PLAYLISTS
        ) {
            error(
                "Maximum number of playlists reached"
            )
        }

        val playlist =
            Playlist(
                id =
                    UUID.randomUUID()
                        .toString(),
                name =
                    name,
                spotifyPlaylistId =
                    spotifyPlaylistId
            )

        connection.prepareStatement(
            """
            INSERT INTO playlists (
                id,
                name,
                spotify_playlist_id
            )
            VALUES (?, ?, ?)
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                playlist.id
            )

            statement.setString(
                2,
                playlist.name
            )

            statement.setString(
                3,
                playlist.spotifyPlaylistId
            )

            statement.executeUpdate()
        }

        return playlist
    }

    fun delete(
        id: String
    ): Boolean {

        connection.prepareStatement(
            """
            DELETE FROM playlists
            WHERE id = ?
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                id
            )

            return statement.executeUpdate() > 0
        }
    }
}