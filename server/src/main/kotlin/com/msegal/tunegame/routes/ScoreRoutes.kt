package com.msegal.tunegame.routes

import com.msegal.tunegame.api.CreateScoreRequest
import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.game.GameSessionManager
import com.msegal.tunegame.score.ScoreRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
                ErrorResponse("playerName is required")
            )
            return@post
        }

        if (request.gameId.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("gameId is required")
            )
            return@post
        }

        val session =
            sessionManager.getGame(request.gameId)

        if (session == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("Game not found")
            )
            return@post
        }

        if (!session.engine.state.gameOver) {
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("Game is not over")
            )
            return@post
        }

        if (session.scoreSubmitted) {
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    "Score has already been submitted"
                )
            )
            return@post
        }

        val score =
            scoreRepository.create(
                playerName = request.playerName,
                score = session.engine.state.score
            )

        session.markScoreSubmitted()

        call.respond(
            HttpStatusCode.Created,
            score
        )
    }
}