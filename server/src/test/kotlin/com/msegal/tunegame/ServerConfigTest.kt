package com.msegal.tunegame

import kotlin.test.*

class ServerConfigTest {
    private val signingKey = "a".repeat(32)

    @Test
    fun `empty environment provides working local defaults with a random signing key`() {
        val first = ServerConfig.fromEnvironment(emptyMap())
        val second = ServerConfig.fromEnvironment(emptyMap())

        assertEquals("http://127.0.0.1:8080", first.publicUrl)
        assertEquals(8080, first.port)
        assertEquals("jdbc:sqlite:tunegame.db", first.databaseUrl)
        assertTrue(first.sessionSigningKey.size >= 32)
        assertFalse(first.sessionSigningKey.contentEquals(second.sessionSigningKey))
    }

    @Test
    fun `public HTTPS origin requires a stable signing key`() {
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(mapOf("PUBLIC_URL" to "https://game.example.com"))
        }

        val config = ServerConfig.fromEnvironment(mapOf(
            "PUBLIC_URL" to "https://game.example.com",
            "SESSION_SIGNING_KEY" to signingKey
        ))
        assertEquals("https://game.example.com", config.publicUrl)
        assertContentEquals(signingKey.toByteArray(Charsets.UTF_8), config.sessionSigningKey)
    }

    @Test
    fun `plain HTTP is restricted to explicit loopback hosts`() {
        for (origin in listOf("http://localhost:8080", "http://127.0.0.1:9090")) {
            assertEquals(origin, ServerConfig.fromEnvironment(mapOf("PUBLIC_URL" to origin)).publicUrl)
        }
        for (origin in listOf("http://game.example.com", "http://0.0.0.0", "http://localhost.example.com")) {
            assertFailsWith<IllegalArgumentException>(origin) {
                ServerConfig.fromEnvironment(mapOf("PUBLIC_URL" to origin, "SESSION_SIGNING_KEY" to signingKey))
            }
        }
    }

    @Test
    fun `public URL rejects anything other than an absolute HTTP or HTTPS origin`() {
        val invalidOrigins = listOf(
            "game.example.com",
            "//game.example.com",
            "ftp://game.example.com",
            "https://",
            "https://user:password@game.example.com",
            "https://game.example.com/play",
            "https://game.example.com?next=elsewhere",
            "https://game.example.com#fragment"
        )
        for (origin in invalidOrigins) {
            assertFailsWith<IllegalArgumentException>(origin) {
                ServerConfig.fromEnvironment(mapOf("PUBLIC_URL" to origin, "SESSION_SIGNING_KEY" to signingKey))
            }
        }
    }

    @Test
    fun `configured signing keys must contain at least thirty two UTF8 bytes`() {
        for (key in listOf("", "a".repeat(31), "é".repeat(15))) {
            assertFailsWith<IllegalArgumentException>("Key with ${key.toByteArray(Charsets.UTF_8).size} bytes") {
                ServerConfig.fromEnvironment(mapOf("SESSION_SIGNING_KEY" to key))
            }
        }
        val unicodeKey = "é".repeat(16)
        val config = ServerConfig.fromEnvironment(mapOf("SESSION_SIGNING_KEY" to unicodeKey))
        assertContentEquals(unicodeKey.toByteArray(Charsets.UTF_8), config.sessionSigningKey)
    }

    @Test
    fun `port accepts the valid range and rejects invalid configuration`() {
        for (port in listOf(1, 8080, 65535)) {
            assertEquals(port, ServerConfig.fromEnvironment(mapOf("PORT" to port.toString())).port)
        }
        for (port in listOf("", "0", "-1", "65536", "2147483648", "abc", "8080.0")) {
            assertFailsWith<IllegalArgumentException>(port) {
                ServerConfig.fromEnvironment(mapOf("PORT" to port))
            }
        }
    }
}
