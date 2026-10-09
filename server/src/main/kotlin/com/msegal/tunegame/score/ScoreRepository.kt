package com.msegal.tunegame.score

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.jvm.Synchronized

class ScoreRepository(
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
                CREATE TABLE IF NOT EXISTS scores (
                    id TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    score INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Synchronized
    fun getAll(): List<Score> {

        val scores =
            mutableListOf<Score>()

        connection.prepareStatement(
            """
            SELECT
                id,
                player_name,
                score
            FROM scores
            ORDER BY score DESC
            """.trimIndent()
        ).use { statement ->

            statement.executeQuery().use { result ->

                while (result.next()) {

                    scores += Score(
                        id =
                            result.getString("id"),
                        playerName =
                            result.getString(
                                "player_name"
                            ),
                        score =
                            result.getInt("score")
                    )
                }
            }
        }

        return scores
    }

    @Synchronized
    fun create(
        playerName: String,
        score: Int
    ): Score {

        require(
            playerName.isNotBlank()
        ) {
            "Player name cannot be blank"
        }

        require(
            score >= 0
        ) {
            "Score cannot be negative"
        }

        val newScore =
            Score(
                id =
                    UUID.randomUUID()
                        .toString(),
                playerName =
                    playerName,
                score =
                    score
            )

        connection.prepareStatement(
            """
            INSERT INTO scores (
                id,
                player_name,
                score
            )
            VALUES (?, ?, ?)
            """.trimIndent()
        ).use { statement ->

            statement.setString(
                1,
                newScore.id
            )

            statement.setString(
                2,
                newScore.playerName
            )

            statement.setInt(
                3,
                newScore.score
            )

            statement.executeUpdate()
        }

        return newScore
    }
}