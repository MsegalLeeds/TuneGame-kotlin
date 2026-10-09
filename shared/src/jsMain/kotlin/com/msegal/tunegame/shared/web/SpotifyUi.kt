package com.msegal.tunegame.shared.web

import com.msegal.tunegame.shared.api.createTuneGameApi
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement

private const val SERVER_URL =
    "http://127.0.0.1:8080"

private val spotifyScope =
    MainScope()

private val spotifyApi =
    createTuneGameApi(
        SERVER_URL
    )

fun setupSpotifyUi() {

    createSpotifyControls()

    val connectButton =
        document.getElementById(
            "spotify-connect-btn"
        ) as HTMLButtonElement

    val disconnectButton =
        document.getElementById(
            "spotify-disconnect-btn"
        ) as HTMLButtonElement

    connectButton.onclick = {

        window.location.href =
            "$SERVER_URL/spotify/login"

        null
    }

    disconnectButton.onclick = {

        spotifyScope.launch {

            try {

                spotifyApi.disconnectSpotify()

                updateSpotifyStatus(
                    connected = false,
                    message =
                        "Spotify not connected"
                )

            } catch (e: Exception) {

                updateSpotifyStatus(
                    connected = false,
                    message =
                        e.message
                            ?: "Could not disconnect Spotify",
                    error = true
                )
            }
        }

        null
    }

    spotifyScope.launch {
        refreshSpotifyStatus()
    }
}

private fun createSpotifyControls() {

    if (
        document.getElementById(
            "spotify-status"
        ) != null
    ) {
        return
    }

    val playlistStatus =
        document.getElementById(
            "playlist-status"
        )
            ?: return

    val container =
        document.createElement(
            "div"
        ) as HTMLElement

    container.style.marginBottom =
        "16px"

    container.innerHTML =
        """
        <div class="mode-label">
            spotify account
        </div>

        <div
            style="
                display: flex;
                align-items: center;
                gap: 8px;
            "
        >
            <div
                id="spotify-status"
                style="
                    flex: 1;
                    font-size: 13px;
                    color: var(--muted);
                "
            >
                Checking Spotify...
            </div>

            <button
                id="spotify-connect-btn"
                class="mode-btn"
                type="button"
            >
                connect
            </button>

            <button
                id="spotify-disconnect-btn"
                class="mode-btn"
                type="button"
                style="display: none;"
            >
                disconnect
            </button>
        </div>
        """.trimIndent()

    playlistStatus.parentNode
        ?.insertBefore(
            container,
            playlistStatus
        )
}

private suspend fun refreshSpotifyStatus() {

    try {

        val status =
            spotifyApi.getSpotifyStatus()

        if (status.connected) {

            updateSpotifyStatus(
                connected = true,
                message =
                    "Spotify connected"
            )

        } else {

            updateSpotifyStatus(
                connected = false,
                message =
                    "Spotify not connected"
            )
        }

    } catch (e: Exception) {

        updateSpotifyStatus(
            connected = false,
            message =
                "Spotify unavailable",
            error = true
        )
    }
}

private fun updateSpotifyStatus(
    connected: Boolean,
    message: String,
    error: Boolean = false
) {

    val status =
        document.getElementById(
            "spotify-status"
        ) as HTMLElement

    val connectButton =
        document.getElementById(
            "spotify-connect-btn"
        ) as HTMLButtonElement

    val disconnectButton =
        document.getElementById(
            "spotify-disconnect-btn"
        ) as HTMLButtonElement

    status.textContent =
        message

    status.style.color =
        when {
            error ->
                "var(--red)"

            connected ->
                "var(--green)"

            else ->
                "var(--muted)"
        }

    connectButton.style.display =
        if (connected) {
            "none"
        } else {
            "block"
        }

    disconnectButton.style.display =
        if (connected) {
            "block"
        } else {
            "none"
        }
}