package com.msegal.tunegame.spotify

import java.sql.Connection
import java.sql.DriverManager

class SpotifyTokenRepository(
    databaseUrl: String = "jdbc:sqlite:tunegame.db"
) {

    private val connection: Connection =
        DriverManager.getConnection(databaseUrl)

    init {
        createTable()
    }

    private fun createTable() {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS spotify_auth (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    refresh_token TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    fun saveRefreshToken(
        refreshToken: String
    ) {
        connection.prepareStatement(
            """
            INSERT INTO spotify_auth (
                id,
                refresh_token
            )
            VALUES (1, ?)
            ON CONFLICT(id)
            DO UPDATE SET
                refresh_token = excluded.refresh_token
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                refreshToken
            )

            statement.executeUpdate()
        }
    }

    fun getRefreshToken(): String? {

        connection.prepareStatement(
            """
            SELECT refresh_token
            FROM spotify_auth
            WHERE id = 1
            """.trimIndent()
        ).use { statement ->

            statement.executeQuery().use { results ->

                if (!results.next()) {
                    return null
                }

                return results.getString(
                    "refresh_token"
                )
            }
        }
    }

    fun deleteRefreshToken(): Boolean {

        connection.prepareStatement(
            """
            DELETE FROM spotify_auth
            WHERE id = 1
            """.trimIndent()
        ).use { statement ->

            return statement.executeUpdate() > 0
        }
    }
}