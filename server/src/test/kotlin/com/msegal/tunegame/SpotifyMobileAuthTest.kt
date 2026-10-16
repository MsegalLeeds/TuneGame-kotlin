package com.msegal.tunegame

import com.msegal.tunegame.routes.spotifyRoutes
import com.msegal.tunegame.session.UserSession
import com.msegal.tunegame.session.getOrCreateUserSession
import com.msegal.tunegame.spotify.*
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.testApplication
import kotlin.test.*

class SpotifyMobileAuthTest {
    private val config = SpotifyConfig("client", "secret", "https://game.example/spotify/callback")

    @Test
    fun `browser authorization connects only the originating mobile session and cannot replay`() = testApplication {
        val exchanged = mutableListOf<String>()
        val tokens = tokenManager(exchanged)
        application {
            install(ContentNegotiation) { json() }
            install(Sessions) {
                cookie<UserSession>("session") {
                    cookie.httpOnly = true
                    cookie.extensions["SameSite"] = "Lax"
                    transform(SessionTransportTransformerMessageAuthentication(ByteArray(32) { 1 }))
                }
            }
            routing {
                get("/session") { call.respondText(call.getOrCreateUserSession().id) }
                spotifyRoutes(SpotifyAuth(config), tokens, { error("not used") }, "https://game.example/play", { false })
            }
        }
        val mobile = createClient { followRedirects = false; install(HttpCookies) }
        val browser = createClient { followRedirects = false; install(HttpCookies) }
        val unrelated = createClient { followRedirects = false; install(HttpCookies) }
        val mobileSession = mobile.get("/session").bodyAsText()
        val browserSession = browser.get("/session").bodyAsText()
        val unrelatedSession = unrelated.get("/session").bodyAsText()
        val start = mobile.get("/spotify/mobile/start")
        assertEquals(HttpStatusCode.Found, start.status)
        assertEquals("no-store", start.headers[HttpHeaders.CacheControl])
        val handoff = start.headers[HttpHeaders.Location]!!
        val authorize = browser.get(handoff)
        assertEquals(HttpStatusCode.Found, authorize.status)
        val spotify = Url(authorize.headers[HttpHeaders.Location]!!)
        assertEquals("accounts.spotify.com", spotify.host)
        val state = spotify.parameters["state"]!!
        val callback = "/spotify/callback?code=code&state=$state"
        assertEquals(HttpStatusCode.BadRequest, unrelated.get(callback).status)
        assertTrue(exchanged.isEmpty())
        val completed = browser.get(callback)
        assertEquals(HttpStatusCode.OK, completed.status)
        assertTrue(completed.bodyAsText().contains("Return to TuneGame"))
        assertEquals(listOf("code"), exchanged)
        assertTrue(tokens.hasStoredAuth(mobileSession))
        assertFalse(tokens.hasStoredAuth(browserSession))
        assertFalse(tokens.hasStoredAuth(unrelatedSession))
        assertEquals(HttpStatusCode.BadRequest, browser.get(handoff).status)
        assertEquals(HttpStatusCode.BadRequest, browser.get(callback).status)
        assertEquals(1, exchanged.size)
    }

    @Test
    fun `denied mobile authorization consumes state without exchanging tokens`() = testApplication {
        val exchanged = mutableListOf<String>()
        val tokens = tokenManager(exchanged)
        application {
            install(ContentNegotiation) { json() }
            install(Sessions) {
                cookie<UserSession>("session") {
                    cookie.httpOnly = true
                    cookie.extensions["SameSite"] = "Lax"
                    transform(SessionTransportTransformerMessageAuthentication(ByteArray(32) { 1 }))
                }
            }
            routing { spotifyRoutes(SpotifyAuth(config), tokens, { error("not used") }, "/play", { false }) }
        }
        val mobile = createClient { followRedirects = false; install(HttpCookies) }
        val browser = createClient { followRedirects = false; install(HttpCookies) }
        val handoff = mobile.get("/spotify/mobile/start").headers[HttpHeaders.Location]!!
        val state = Url(browser.get(handoff).headers[HttpHeaders.Location]!!).parameters["state"]!!
        val denied = browser.get("/spotify/callback?error=access_denied&state=$state")
        assertEquals(HttpStatusCode.OK, denied.status)
        assertTrue(denied.bodyAsText().contains("not connected"))
        assertEquals(HttpStatusCode.BadRequest, browser.get("/spotify/callback?code=code&state=$state").status)
        assertTrue(exchanged.isEmpty())
    }

    @Test
    fun `ordinary browser authorization still connects its own session and redirects to play`() = testApplication {
        val tokens = tokenManager(mutableListOf())
        application {
            install(ContentNegotiation) { json() }
            install(Sessions) {
                cookie<UserSession>("session") {
                    cookie.httpOnly = true
                    cookie.extensions["SameSite"] = "Lax"
                    transform(SessionTransportTransformerMessageAuthentication(ByteArray(32) { 1 }))
                }
            }
            routing {
                get("/session") { call.respondText(call.getOrCreateUserSession().id) }
                spotifyRoutes(SpotifyAuth(config), tokens, { error("not used") }, "https://game.example/play", { false })
            }
        }
        val browser = createClient { followRedirects = false; install(HttpCookies) }
        val session = browser.get("/session").bodyAsText()
        val state = Url(browser.get("/spotify/login").headers[HttpHeaders.Location]!!).parameters["state"]!!
        val callback = browser.get("/spotify/callback?code=code&state=$state")
        assertEquals(HttpStatusCode.Found, callback.status)
        assertEquals("https://game.example/play/?spotify=connected", callback.headers[HttpHeaders.Location])
        assertTrue(tokens.hasStoredAuth(session))
    }

    @Test
    fun `token exchange failure leaves mobile disconnected and consumes callback`() = testApplication {
        val exchanges = mutableListOf<String>()
        application {
            install(ContentNegotiation) { json() }
            install(Sessions) { cookie<UserSession>("session") }
            routing {
                spotifyRoutes(SpotifyAuth(config), tokenManager(exchanges, failExchange = true),
                    { error("not used") }, "/play", { false })
            }
        }
        val mobile = createClient { followRedirects = false; install(HttpCookies) }
        val browser = createClient { followRedirects = false; install(HttpCookies) }
        val handoff = mobile.get("/spotify/mobile/start").headers[HttpHeaders.Location]!!
        val state = Url(browser.get(handoff).headers[HttpHeaders.Location]!!).parameters["state"]!!
        val callback = "/spotify/callback?code=code&state=$state"
        val failed = browser.get(callback)
        assertEquals(HttpStatusCode.OK, failed.status)
        assertTrue(failed.bodyAsText().contains("not connected"))
        assertFalse(failed.bodyAsText().contains("upstream secret"))
        assertEquals(HttpStatusCode.BadRequest, browser.get(callback).status)
        assertEquals(listOf("code"), exchanges)
        assertTrue(mobile.get("/spotify/status").bodyAsText().contains("false"))
    }

    @Test
    fun `unconfigured server rejects mobile sign in without issuing a handoff`() = testApplication {
        application {
            routing { spotifyRoutes(null, null, { error("not used") }, "/play", { false }) }
        }
        val response = client.get("/spotify/mobile/start")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertNull(response.headers[HttpHeaders.Location])
        assertNull(response.headers[HttpHeaders.SetCookie])
    }

    private fun tokenManager(exchanged: MutableList<String>, failExchange: Boolean = false) = SpotifyTokenManager(
        object : SpotifyTokenProvider {
            override suspend fun exchangeCode(code: String): SpotifyToken {
                exchanged += code
                if (failExchange) error("upstream secret")
                return SpotifyToken("access", "Bearer", 3600, "refresh")
            }
            override suspend fun refreshToken(refreshToken: String): SpotifyToken = error("not used")
        },
        SpotifyTokenRepository("jdbc:sqlite::memory:")
    )
}
