package com.msegal.tunegame.shared.web

import com.msegal.tunegame.shared.api.SpotifyPlaylist
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.w3c.dom.*

internal class SpotifyPlaylistPicker(
    private val scope: CoroutineScope,
    private val fetchPlaylists: suspend () -> List<SpotifyPlaylist>,
    private val selectPlaylist: suspend (SpotifyPlaylist) -> Unit
) {
    val element = document.createElement("div") as HTMLElement
    private val select = document.createElement("select") as HTMLSelectElement
    private val refresh = document.createElement("button") as HTMLButtonElement
    private val status = document.createElement("p") as HTMLElement
    private var playlists = emptyList<SpotifyPlaylist>()
    private var connected = false
    private var generation = 0

    init {
        element.hidden = true
        element.className = "spotify-playlist-picker"
        val label = document.createElement("label") as HTMLLabelElement
        label.textContent = "Your Spotify playlists"
        label.htmlFor = "spotify-playlists"
        select.id = "spotify-playlists"
        select.setAttribute("aria-label", "Your Spotify playlists")
        refresh.type = "button"
        refresh.className = "mode-btn"
        refresh.textContent = "Refresh playlists"
        status.setAttribute("role", "status")
        element.appendChild(label)
        element.appendChild(select)
        element.appendChild(refresh)
        element.appendChild(status)
        refresh.onclick = {
            scope.launch { setConnected(connected) }
            null
        }
        select.onchange = {
            val playlist = playlists.find { it.id == select.value }
            if (playlist != null) {
                val selectedGeneration = generation
                select.disabled = true
                refresh.disabled = true
                status.textContent = "Loading ${playlist.name}..."
                scope.launch {
                    try {
                        selectPlaylist(playlist)
                        if (selectedGeneration == generation) status.textContent = "Selected ${playlist.name}."
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (selectedGeneration == generation) {
                            status.textContent = "Could not use this playlist. Try another playlist or refresh."
                            select.value = ""
                        }
                    } finally {
                        if (selectedGeneration == generation) {
                            select.disabled = false
                            refresh.disabled = false
                        }
                    }
                }
            }
            null
        }
    }

    suspend fun setConnected(connected: Boolean) {
        this.connected = connected
        val loadingGeneration = ++generation
        element.hidden = !connected
        playlists = emptyList()
        select.textContent = ""
        select.disabled = true
        refresh.disabled = !connected
        status.textContent = ""
        if (!connected) return
        status.textContent = "Loading your playlists..."
        refresh.disabled = true
        try {
            val loaded = fetchPlaylists()
            if (loadingGeneration != generation) return
            playlists = loaded
            val placeholder = document.createElement("option") as HTMLOptionElement
            placeholder.value = ""
            placeholder.textContent = "Choose a Spotify playlist"
            select.appendChild(placeholder)
            for (playlist in loaded) {
                val option = document.createElement("option") as HTMLOptionElement
                option.value = playlist.id
                option.textContent = playlist.name
                select.appendChild(option)
            }
            select.disabled = loaded.isEmpty()
            status.textContent = if (loaded.isEmpty()) "No playlists found in this Spotify account."
                else "Choose a playlist to use in your game."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (loadingGeneration == generation) status.textContent = "Could not load your playlists. Try Refresh playlists."
        } finally {
            if (loadingGeneration == generation) refresh.disabled = false
        }
    }
}
