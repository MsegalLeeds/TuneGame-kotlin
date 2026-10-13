package com.msegal.tunegame

import java.net.URI
import java.security.SecureRandom

/** One origin serves both the browser and API. Keep the signing key stable across restarts. */
data class ServerConfig(
    val publicUrl: String = "http://127.0.0.1:8080",
    val port: Int = 8080,
    val databaseUrl: String = "jdbc:sqlite:tunegame.db",
    val sessionSigningKey: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
) {
    val secureCookies: Boolean get() = publicUrl.startsWith("https://")

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ServerConfig {
            val publicUrl = environment["PUBLIC_URL"] ?: "http://127.0.0.1:8080"
            val uri = URI.create(publicUrl)
            val local = uri.host in setOf("localhost", "127.0.0.1")
            require(uri.host != null && (uri.scheme == "https" || (local && uri.scheme == "http")) &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.isNullOrEmpty() && (uri.port == -1 || uri.port in 1..65535)) {
                "PUBLIC_URL must be an HTTPS origin without a path (HTTP is allowed only on loopback)"
            }
            val port = environment["PORT"]?.let {
                requireNotNull(it.toIntOrNull()) { "PORT must be an integer" }
            } ?: 8080
            require(port in 1..65535) { "PORT must be between 1 and 65535" }
            val key = environment["SESSION_SIGNING_KEY"]?.toByteArray(Charsets.UTF_8)
            require(key == null || key.size >= 32) { "SESSION_SIGNING_KEY must have at least 32 bytes" }
            require(local || key != null) { "SESSION_SIGNING_KEY is required for a public deployment" }
            return ServerConfig(
                publicUrl, port, environment["DATABASE_URL"] ?: "jdbc:sqlite:tunegame.db",
                key ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
            )
        }
    }
}
