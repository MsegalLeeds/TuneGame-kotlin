package com.msegal.tunegame.session

import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import java.util.UUID

fun ApplicationCall.getOrCreateUserSession(): UserSession {

    val existing =
        sessions.get<UserSession>()

    if (existing != null) {
        return existing
    }

    val session =
        UserSession(
            id =
                UUID.randomUUID()
                    .toString()
        )

    sessions.set(session)

    return session
}