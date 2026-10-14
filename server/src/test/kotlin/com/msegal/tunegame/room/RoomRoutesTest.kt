package com.msegal.tunegame.room

import com.msegal.tunegame.module
import com.msegal.tunegame.repository.SongRepository
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class RoomRoutesTest {
    @Test fun `independent clients join play and cannot impersonate the host`() = testApplication {
        val db = Files.createTempFile("room-routes", ".db").toFile().apply { deleteOnExit() }
        application { module(SongRepository().loadSongs(), databaseUrl = "jdbc:sqlite:${db.absolutePath}") }
        val host = createClient { install(HttpCookies) }
        val guest = createClient { install(HttpCookies) }
        val stranger = createClient { install(HttpCookies) }
        val created = host.post("/api/rooms") { contentType(ContentType.Application.Json); setBody("""{"name":"Host"}""") }
        assertEquals(HttpStatusCode.OK, created.status)
        val room = Json.parseToJsonElement(created.bodyAsText()).jsonObject
        val code = room.getValue("code").jsonPrimitive.content
        assertEquals(HttpStatusCode.NotFound, stranger.get("/api/rooms/$code").status)
        assertEquals(HttpStatusCode.OK, guest.post("/api/rooms/$code/join") { contentType(ContentType.Application.Json); setBody("""{"name":"Friend"}""") }.status)
        assertEquals(HttpStatusCode.Forbidden, guest.post("/api/rooms/$code/start").status)
        val started = host.post("/api/rooms/$code/start")
        assertEquals(HttpStatusCode.OK, started.status)
        val hostView = Json.parseToJsonElement(started.bodyAsText()).jsonObject
        val guestView = Json.parseToJsonElement(guest.get("/api/rooms/$code").bodyAsText()).jsonObject
        assertEquals(hostView["question"], guestView["question"])
        assertEquals(JsonNull, guestView["correctAnswer"])
        assertNotEquals(hostView["you"], guestView["you"])
        val answer = """{"round":1,"answer":"wrong"}"""
        host.post("/api/rooms/$code/answer") { contentType(ContentType.Application.Json); setBody(answer) }
        val result = guest.post("/api/rooms/$code/answer") { contentType(ContentType.Application.Json); setBody(answer) }
        val resultJson = Json.parseToJsonElement(result.bodyAsText()).jsonObject
        assertEquals("RESULTS", resultJson.getValue("phase").jsonPrimitive.content)
        assertNotEquals(JsonNull, resultJson["correctAnswer"])
        assertEquals(HttpStatusCode.BadRequest, guest.post("/api/rooms/$code/answer") { contentType(ContentType.Application.Json); setBody("{}") }.status)
        assertEquals(HttpStatusCode.OK, guest.post("/api/rooms/$code/leave").status)
        assertEquals(HttpStatusCode.NotFound, guest.get("/api/rooms/$code").status)
    }
}
