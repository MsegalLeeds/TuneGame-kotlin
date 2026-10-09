package com.msegal.tunegame.spotify

import java.sql.Connection
import java.sql.DriverManager
import kotlin.jvm.Synchronized

class SpotifyTokenRepository(
    databaseUrl: String = "jdbc:sqlite:tunegame.db"
) {

    private val connection: Connection =
        DriverManager.getConnection(databaseUrl)

    init {
        connection.createStatement().use {
            it.execute(
                "PRAGMA busy_timeout = 5000"
            )
        }

        createTable()
    }

    private fun createTable() {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS spotify_session_auth (
                    session_id TEXT PRIMARY KEY,
                    refresh_token TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Synchronized
    fun saveRefreshToken(
        sessionId: String,
        refreshToken: String
    ) {

        require(sessionId.isNotBlank()) {
            "Session ID cannot be blank"
        }

        require(refreshToken.isNotBlank()) {
            "Refresh token cannot be blank"
        }

        connection.prepareStatement(
            """
            INSERT INTO spotify_session_auth (
                session_id,
                refresh_token
            )
            VALUES (?, ?)
            ON CONFLICT(session_id)
            DO UPDATE SET
                refresh_token = excluded.refresh_token
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                sessionId
            )

            statement.setString(
                2,
                refreshToken
            )

            statement.executeUpdate()
        }
    }

    @Synchronized
    fun getRefreshToken(
        sessionId: String
    ): String? {

        connection.prepareStatement(
            """
            SELECT refresh_token
            FROM spotify_session_auth
            WHERE session_id = ?
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                sessionId
            )

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

    @Synchronized
    fun deleteRefreshToken(
        sessionId: String
    ): Boolean {

        connection.prepareStatement(
            """
            DELETE FROM spotify_session_auth
            WHERE session_id = ?
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                sessionId
            )

            return statement.executeUpdate() > 0
        }
    }
}