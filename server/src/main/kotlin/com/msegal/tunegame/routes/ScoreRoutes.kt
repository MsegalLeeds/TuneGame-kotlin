package com.msegal.tunegame.routes

import com.msegal.tunegame.api.CreateScoreRequest
import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.game.GameSessionManager
import com.msegal.tunegame.score.Score
import com.msegal.tunegame.score.ScoreRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.scoreRoutes(
    scoreRepository: ScoreRepository,
    sessionManager: GameSessionManager
) {

    get("/scores") {
        call.respond(
            scoreRepository.getAll()
        )
    }

    post("/scores") {
        val request =
            call.receive<CreateScoreRequest>()

        if (request.playerName.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "playerName is required"
                )
            )
            return@post
        }

        if (request.gameId.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    "gameId is required"
                )
            )
            return@post
        }

        val session =
            sessionManager.getGame(
                request.gameId
            )

        if (session == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    "Game not found"
                )
            )
            return@post
        }

        val outcome =
            synchronized(session) {

                when {
                    session.scoreSubmitted -> {
                        ScoreSubmissionOutcome(
                            error =
                                "Score has already been submitted"
                        )
                    }

                    !session.engine.state.gameOver -> {
                        ScoreSubmissionOutcome(
                            error =
                                "Game is not over"
                        )
                    }

                    else -> {
                        val score =
                            scoreRepository.create(
                                playerName =
                                    request.playerName,
                                score =
                                    session.engine.state.score
                            )

                        session.markScoreSubmitted()

                        ScoreSubmissionOutcome(
                            score = score
                        )
                    }
                }
            }

        if (outcome.error != null) {
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    outcome.error
                )
            )

            return@post
        }

        val score =
            outcome.score
                ?: error(
                    "Score submission completed without a score"
                )

        call.respond(
            HttpStatusCode.Created,
            score
        )
    }
}

private data class ScoreSubmissionOutcome(
    val score: Score? = null,
    val error: String? = null
)