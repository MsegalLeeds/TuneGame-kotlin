package com.msegal.tunegame

import com.msegal.tunegame.api.*
import com.msegal.tunegame.game.*
import com.msegal.tunegame.repository.SongRepository
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

const val TIME_LIMIT_SECONDS = 30.0

fun Application.module(songs: List<Song> = SongRepository().loadSongs()) {

    install(ContentNegotiation) {
        json()
    }

    val sessionManager = GameSessionManager(songs)

    routing {

        get("/") {
            call.respondText("TuneGame server is running!")
        }

        post("/new-game") {

            val gameId = sessionManager.createGame()

            val session = sessionManager.getGame(gameId)!!

            call.respond(
                NewGameResponse(
                    ok = true,
                    message = "Game created",
                    gameId = gameId,
                    lives = session.engine.state.lives
                )
            )
        }

        get("/question") {

            val gameId = call.request.queryParameters["gameId"]

            if (gameId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("gameId is required")
                )
                return@get
            }

            val session = sessionManager.getGame(gameId)

            if (session == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Game not found")
                )
                return@get
            }

            if (session.engine.state.lives <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Game is over")
                )
                return@get
            }

            val question = session.engine.generateQuestion(
                QuestionType.entries.random()
            )

            session.startQuestion(question)

            call.respond(
                QuestionResponse(question)
            )
        }

        post("/answer") {

            val request = call.receive<AnswerRequest>()

            val session = sessionManager.getGame(request.gameId)

            if (session == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("Game not found")
                )
                return@post
            }

            val question = session.currentQuestion

            if (question == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("No active question")
                )
                return@post
            }

            val elapsedSeconds = session.elapsedSeconds()

            if (elapsedSeconds > TIME_LIMIT_SECONDS) {

                val result = session.engine.checkAnswer(
                    answer = "",
                    question = question,
                    elapsedSeconds = elapsedSeconds
                )

                session.clearQuestion()

                call.respond(result)
                return@post
            }

            val result = session.engine.checkAnswer(
                answer = request.answer,
                question = question,
                elapsedSeconds = elapsedSeconds
            )

            session.clearQuestion()

            call.respond(result)
        }
    }

    println("TuneGame server running on http://localhost:8080")
}