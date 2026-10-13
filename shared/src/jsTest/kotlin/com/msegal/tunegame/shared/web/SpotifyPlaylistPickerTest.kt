package com.msegal.tunegame.shared.web

import com.msegal.tunegame.shared.api.SpotifyPlaylist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyPlaylistPickerTest {
    @Test
    fun connectedPickerRendersAccessibleOptionsAsText() = runTest {
        val unsafeName = "<img src=x onerror=alert(1)> Rock & roll"
        val picker = SpotifyPlaylistPicker(
            scope = this,
            fetchPlaylists = { listOf(SpotifyPlaylist("private-id", unsafeName)) },
            selectPlaylist = {}
        )

        picker.setConnected(true)

        val select = picker.select()
        assertFalse(picker.element.isHidden())
        assertEquals("Your Spotify playlists", select.getAttribute("aria-label"))
        assertFalse(select.disabled)
        assertEquals(2, select.options.length)
        val placeholder = select.options.item(0) as HTMLOptionElement
        assertEquals("", placeholder.value)
        val option = select.options.item(1) as HTMLOptionElement
        assertEquals("private-id", option.value)
        assertEquals(unsafeName, option.textContent)
        assertNull(picker.element.querySelector("img"))
        assertEquals("Refresh playlists", picker.refreshButton().textContent)
        assertNotNull(picker.element.querySelector("p[role='status']"))
    }

    @Test
    fun emptyAccountDisablesSelectionAndExplainsTheEmptyState() = runTest {
        val picker = SpotifyPlaylistPicker(this, { emptyList() }, {})

        picker.setConnected(true)

        assertFalse(picker.element.isHidden())
        assertTrue(picker.select().disabled)
        assertTrue(picker.status().textContent.orEmpty().isNotBlank())
        assertTrue(picker.select().options.length <= 1)
    }

    @Test
    fun fetchFailureDisablesSelectionAndShowsAStatus() = runTest {
        val picker = SpotifyPlaylistPicker(this, { error("upstream failed") }, {})

        picker.setConnected(true)

        assertTrue(picker.select().disabled)
        assertTrue(picker.status().textContent.orEmpty().isNotBlank())
        assertTrue(picker.select().options.length <= 1)
    }

    @Test
    fun changingSelectionCallsBackOnlyForAKnownPlaylist() = runTest {
        val playlist = SpotifyPlaylist("known-id", "My playlist")
        val selected = mutableListOf<SpotifyPlaylist>()
        val picker = SpotifyPlaylistPicker(this, { listOf(playlist) }, { selected += it })
        picker.setConnected(true)

        picker.select().value = "known-id"
        picker.select().dispatchEvent(Event("change"))
        runCurrent()
        assertEquals(listOf(playlist), selected)

        picker.select().value = ""
        picker.select().dispatchEvent(Event("change"))
        runCurrent()
        picker.select().value = "unknown-id"
        picker.select().dispatchEvent(Event("change"))
        runCurrent()
        assertEquals(listOf(playlist), selected)
    }

    @Test
    fun refreshReplacesOptionsWithTheLatestPlaylists() = runTest {
        var fetches = 0
        val picker = SpotifyPlaylistPicker(this, {
            fetches++
            listOf(SpotifyPlaylist("playlist-$fetches", "Playlist $fetches"))
        }, {})
        picker.setConnected(true)

        picker.refreshButton().click()
        runCurrent()

        assertEquals(2, fetches)
        assertEquals(2, picker.select().options.length)
        assertEquals("playlist-2", (picker.select().options.item(1) as HTMLOptionElement).value)
        assertFalse(picker.element.textContent.orEmpty().contains("Playlist 1"))
    }

    @Test
    fun disconnectHidesThePickerAndClearsPrivateOptions() = runTest {
        val selected = mutableListOf<SpotifyPlaylist>()
        val picker = SpotifyPlaylistPicker(this,
            { listOf(SpotifyPlaylist("private-id", "Private playlist")) },
            { selected += it })
        picker.setConnected(true)

        picker.setConnected(false)

        assertTrue(picker.element.isHidden())
        assertEquals(0, picker.select().options.length)
        assertFalse(picker.element.textContent.orEmpty().contains("Private playlist"))
        picker.select().value = "private-id"
        picker.select().dispatchEvent(Event("change"))
        runCurrent()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun completedFetchCannotRestorePrivateOptionsAfterDisconnect() = runTest {
        val pending = CompletableDeferred<List<SpotifyPlaylist>>()
        val picker = SpotifyPlaylistPicker(this, { pending.await() }, {})
        val loading = launch { picker.setConnected(true) }
        runCurrent()

        picker.setConnected(false)
        pending.complete(listOf(SpotifyPlaylist("stale-id", "Stale private playlist")))
        loading.join()
        runCurrent()

        assertTrue(picker.element.isHidden())
        assertEquals(0, picker.select().options.length)
        assertFalse(picker.element.textContent.orEmpty().contains("Stale private playlist"))
    }

    private fun SpotifyPlaylistPicker.select(): HTMLSelectElement =
        assertNotNull(element.querySelector("select")) as HTMLSelectElement

    private fun SpotifyPlaylistPicker.refreshButton(): HTMLButtonElement =
        assertNotNull(element.querySelector("button")) as HTMLButtonElement

    private fun SpotifyPlaylistPicker.status(): HTMLElement =
        assertNotNull(element.querySelector("p[role='status']")) as HTMLElement

    private fun HTMLElement.isHidden(): Boolean = hidden || style.display == "none"
}
