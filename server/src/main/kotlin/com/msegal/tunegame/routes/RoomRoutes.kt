package com.msegal.tunegame.routes

import com.msegal.tunegame.room.PrivateRooms
import com.msegal.tunegame.room.RoomFailure
import com.msegal.tunegame.api.ErrorResponse
import com.msegal.tunegame.session.getOrCreateUserSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable private data class RoomName(val name: String)
@Serializable private data class RoomAnswer(val round: Int, val answer: String)
@Serializable private data class RoomRound(val round: Int)

fun Route.roomRoutes(rooms: PrivateRooms) {
    route("/api/rooms") {
        post { call.roomResponse { rooms.create(call.getOrCreateUserSession().id, call.receive<RoomName>().name) } }
        route("/{code}") {
            get { call.roomResponse { rooms.state(call.parameters["code"].orEmpty(), call.getOrCreateUserSession().id) } }
            post("/join") { call.roomResponse { rooms.join(call.parameters["code"].orEmpty(), call.getOrCreateUserSession().id, call.receive<RoomName>().name) } }
            post("/start") { call.roomResponse { rooms.start(call.parameters["code"].orEmpty(), call.getOrCreateUserSession().id) } }
            post("/next") { call.roomResponse { rooms.next(call.parameters["code"].orEmpty(), call.getOrCreateUserSession().id, call.receive<RoomRound>().round) } }
            post("/answer") { call.roomResponse {
                val body = call.receive<RoomAnswer>()
                rooms.answer(call.parameters["code"].orEmpty(), call.getOrCreateUserSession().id, body.round, body.answer)
            } }
            post("/leave") { call.roomResponse {
                rooms.leave(call.parameters["code"].orEmpty(), call.getOrCreateUserSession().id)
                mapOf("ok" to true)
            } }
        }
    }
}
private suspend inline fun <reified T : Any> ApplicationCall.roomResponse(block: () -> T) {
    try { respond(block()) } catch (e: RoomFailure) { respond(HttpStatusCode.fromValue(e.status), ErrorResponse(e.message)) }
}
