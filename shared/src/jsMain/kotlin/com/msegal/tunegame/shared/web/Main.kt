package com.msegal.tunegame.shared.web

import com.msegal.tunegame.shared.api.createTuneGameApi
import com.msegal.tunegame.shared.game.GameClient
import com.msegal.tunegame.shared.game.GameState
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLOptionElement

private val scope = MainScope()

private const val TIME_LIMIT_SECONDS = 30

private var timerId: Int? = null
private var timeLeft = TIME_LIMIT_SECONDS

private var selectedPlaylistId: String? = null
private var answered = false

private val api =
    createTuneGameApi(
        "http://localhost:8080"
    )

private val gameClient =
    GameClient(api)

fun main() {

    setupButtons()
    setupSpotifyUi()

    scope.launch {
        loadSavedPlaylists()
    }

    scope.launch {
        gameClient.state.collect { state ->
            render(state)
        }
    }
}

private fun setupButtons() {

    /*
     * Start game
     */
    val startButton =
        document.getElementById("start-button")
                as HTMLButtonElement

    startButton.onclick = {
        scope.launch {
            answered = false
            resetResult()
            setStartError("")

            gameClient.newGame(
                selectedPlaylistId
            )

            val state =
                gameClient.state.value

            if (state.error != null) {
                showScreen("screen-start")

                setStartError(
                    state.error
                        ?: "Could not start game."
                )

                return@launch
            }

            showScreen("screen-game")

            gameClient.nextQuestion()
        }

        null
    }

    /*
     * Playlist controls
     */
    val playlistInput =
        document.getElementById("playlist-url")
                as HTMLInputElement

    val playlistLoadButton =
        document.getElementById("playlist-load-btn")
                as HTMLButtonElement

    val defaultPlaylistButton =
        document.getElementById("btn-default-list")
                as HTMLButtonElement

    val savedPlaylists =
        document.getElementById("saved-playlists")
                as HTMLSelectElement

    val deletePlaylistButton =
        document.getElementById("delete-playlist-btn")
                as HTMLButtonElement

    deletePlaylistButton.disabled =
        selectedPlaylistId == null

    savedPlaylists.onchange = {

        selectedPlaylistId =
            savedPlaylists.value
                .takeIf {
                    it.isNotBlank()
                }

        deletePlaylistButton.disabled =
            selectedPlaylistId == null

        val status =
            document.getElementById(
                "playlist-status"
            ) as HTMLElement

        if (selectedPlaylistId == null) {
            status.textContent =
                "Using the built-in classic rock list"

            status.classList.remove(
                "custom"
            )

            defaultPlaylistButton.disabled =
                true

            setPlaylistMessage(
                "Using default playlist.",
                isError = false
            )

        } else {
            val selectedName =
                savedPlaylists.options
                    .item(savedPlaylists.selectedIndex)
                    ?.textContent
                    ?: "Spotify playlist"

            status.textContent =
                "Using $selectedName"

            status.classList.add(
                "custom"
            )

            defaultPlaylistButton.disabled =
                false

            setPlaylistMessage(
                "Using $selectedName.",
                isError = false
            )
        }

        null
    }

    deletePlaylistButton.onclick = deletePlaylistClick@ {

        val playlistId =
            savedPlaylists.value
                .takeIf {
                    it.isNotBlank()
                }

        if (playlistId == null) {
            setPlaylistMessage(
                "Select a saved playlist first.",
                isError = true
            )

            return@deletePlaylistClick null
        }

        scope.launch {
            try {
                api.deletePlaylist(
                    playlistId
                )

                if (selectedPlaylistId == playlistId) {
                    selectedPlaylistId = null
                }

                loadSavedPlaylists()

                savedPlaylists.value = ""
                deletePlaylistButton.disabled = true

                val status =
                    document.getElementById(
                        "playlist-status"
                    ) as HTMLElement

                status.textContent =
                    "Using the built-in classic rock list"

                status.classList.remove(
                    "custom"
                )

                defaultPlaylistButton.disabled =
                    true

                setPlaylistMessage(
                    "Playlist deleted.",
                    isError = false
                )

            } catch (e: Exception) {
                setPlaylistMessage(
                    e.message
                        ?: "Could not delete playlist.",
                    isError = true
                )
            }
        }

        null
    }

    playlistLoadButton.onclick = playlistClick@ {

        val url =
            playlistInput.value.trim()

        if (url.isEmpty()) {
            setPlaylistMessage(
                "Paste a Spotify playlist link.",
                isError = true
            )

            return@playlistClick null
        }

        val spotifyPlaylistId =
            extractSpotifyPlaylistId(url)

        if (spotifyPlaylistId == null) {
            setPlaylistMessage(
                "That does not look like a valid Spotify playlist link.",
                isError = true
            )

            return@playlistClick null
        }

        scope.launch {
            try {
                setPlaylistMessage(
                    "Loading playlist...",
                    isError = false
                )

                val playlist =
                    api.createPlaylist(
                        name = "Spotify Playlist",
                        spotifyPlaylistId =
                            spotifyPlaylistId
                    )

                selectedPlaylistId =
                    playlist.id

                loadSavedPlaylists()

                savedPlaylists.value =
                    playlist.id

                playlistInput.value = ""

                val status =
                    document.getElementById(
                        "playlist-status"
                    ) as HTMLElement

                status.textContent =
                    "Using ${playlist.name}"

                status.classList.add(
                    "custom"
                )

                defaultPlaylistButton.disabled =
                    false

                deletePlaylistButton.disabled =
                    false

                setPlaylistMessage(
                    "${playlist.name} loaded.",
                    isError = false
                )

            } catch (e: Exception) {
                setPlaylistMessage(
                    e.message
                        ?: "Could not load playlist.",
                    isError = true
                )
            }
        }

        null
    }

    defaultPlaylistButton.onclick = {

        selectedPlaylistId = null

        savedPlaylists.value = ""

        val status =
            document.getElementById(
                "playlist-status"
            ) as HTMLElement

        status.textContent =
            "Using the built-in classic rock list"

        status.classList.remove(
            "custom"
        )

        defaultPlaylistButton.disabled =
            true

        deletePlaylistButton.disabled =
            true

        setPlaylistMessage(
            "",
            isError = false
        )

        null
    }

    /*
     * Next question
     */
    val nextButton =
        document.getElementById("next-btn")
                as HTMLButtonElement

    nextButton.onclick = {
        scope.launch {
            answered = false

            resetResult()

            gameClient.nextQuestion()
        }

        null
    }

    /*
     * Play again
     */
    val playAgainButton =
        document.getElementById(
            "play-again-btn"
        ) as HTMLButtonElement

    playAgainButton.onclick = {

        answered = false

        stopTimer()
        resetResult()

        val saveButton =
            document.getElementById(
                "save-score-btn"
            ) as HTMLButtonElement

        saveButton.textContent = "save"
        saveButton.disabled = false

        val nameInput =
            document.getElementById(
                "save-name"
            ) as HTMLInputElement

        nameInput.value = ""

        setGameOverError("")

        showScreen("screen-start")

        null
    }

    /*
     * Save leaderboard score
     */
    val saveScoreButton =
        document.getElementById(
            "save-score-btn"
        ) as HTMLButtonElement

    saveScoreButton.onclick = saveScoreClick@ {

        val nameInput =
            document.getElementById(
                "save-name"
            ) as HTMLInputElement

        val name =
            nameInput.value.trim()

        if (name.isEmpty()) {
            setGameOverError(
                "Enter your name first."
            )

            return@saveScoreClick null
        }

        scope.launch {
            try {
                gameClient.submitScore(
                    name
                )

                saveScoreButton.textContent =
                    "saved!"

                saveScoreButton.disabled =
                    true

                setGameOverError("")

                loadLeaderboard()

            } catch (e: Exception) {
                setGameOverError(
                    e.message
                        ?: "Could not save score."
                )
            }
        }

        null
    }

    /*
     * Question mode
     *
     * Multiple choice works.
     * Typed mode will be added later.
     */
    val multipleChoiceButton =
        document.getElementById(
            "btn-mc"
        ) as HTMLButtonElement

    val typedButton =
        document.getElementById(
            "btn-typed"
        ) as HTMLButtonElement

    multipleChoiceButton.onclick = {

        multipleChoiceButton
            .classList
            .add("active")

        typedButton
            .classList
            .remove("active")

        setStartError("")

        null
    }

    typedButton.onclick = {

        setStartError(
            "Typed-answer mode is not enabled yet."
        )

        null
    }

    /*
     * Playback mode
     *
     * Currently the server starts playback
     * 30 seconds into each song.
     */
    val clipButton =
        document.getElementById(
            "btn-clip"
        ) as HTMLButtonElement

    val fullButton =
        document.getElementById(
            "btn-full"
        ) as HTMLButtonElement

    clipButton.onclick = {

        clipButton
            .classList
            .add("active")

        fullButton
            .classList
            .remove("active")

        setStartError("")

        null
    }

    fullButton.onclick = {

        setStartError(
            "Full-song playback is not enabled yet."
        )

        null
    }
}

private fun render(
    state: GameState
) {
    updateStats(
        state
    )

    val gameError =
        document.getElementById(
            "game-error"
        ) as HTMLElement

    gameError.textContent =
        state.error ?: ""

    if (state.isLoading) {

        val questionText =
            document.getElementById(
                "question-text"
            ) as HTMLElement

        questionText.textContent =
            "Loading..."

        return
    }

    if (state.gameOver) {
        stopTimer()

        showGameOver(
            state
        )

        return
    }

    val question =
        state.question
            ?: return

    showScreen(
        "screen-game"
    )

    val questionText =
        document.getElementById(
            "question-text"
        ) as HTMLElement

    questionText.textContent =
        question.question

    renderAlbumArt(
        question.albumArtUrl
    )

    renderChoices(
        question.choices
    )

    if (
        !answered &&
        timerId == null
    ) {
        startTimer()
    }

    val nowPlaying =
        document.getElementById(
            "now-playing"
        ) as HTMLElement

    nowPlaying.style.display =
        "flex"
}

private fun renderAlbumArt(
    albumArtUrl: String?
) {
    val albumImg =
        document.getElementById(
            "album-img"
        ) as HTMLImageElement

    val placeholder =
        document.getElementById(
            "album-placeholder"
        ) as HTMLElement

    if (albumArtUrl != null) {

        albumImg.src =
            albumArtUrl

        albumImg.classList.add(
            "loaded"
        )

        albumImg.style.transition =
            "none"

        albumImg.style.filter =
            "blur(24px)"

        placeholder.style.display =
            "none"

    } else {

        albumImg.classList.remove(
            "loaded"
        )

        albumImg.removeAttribute(
            "src"
        )

        placeholder.style.display =
            "flex"
    }
}

private fun renderChoices(
    choices: List<String>
) {
    val container =
        document.getElementById(
            "choices-wrap"
        ) as HTMLElement

    container.innerHTML = ""

    val labels =
        listOf(
            "A",
            "B",
            "C",
            "D"
        )

    choices.forEachIndexed {
            index,
            choice ->

        val button =
            document.createElement(
                "button"
            ) as HTMLButtonElement

        button.className =
            "choice-btn fade-in"

        button.style.animationDelay =
            "${index * 0.05}s"

        val label =
            labels.getOrElse(index) {
                "${index + 1}"
            }

        button.innerHTML =
            """
            <span class="choice-key">
                $label
            </span>
            $choice
            """.trimIndent()

        button.onclick = choiceClick@ {

            if (answered) {
                return@choiceClick null
            }

            answered = true

            stopTimer()

            disableChoices()

            scope.launch {

                val oldScore =
                    gameClient
                        .state
                        .value
                        .score

                val newState =
                    gameClient.answer(
                        choice
                    )

                showAnswerResult(
                    selectedButton = button,
                    oldScore = oldScore,
                    newState = newState
                )
            }

            null
        }

        container.appendChild(
            button
        )
    }
}

private fun showAnswerResult(
    selectedButton: HTMLButtonElement,
    oldScore: Int,
    newState: GameState
) {
    val banner =
        document.getElementById(
            "result-banner"
        ) as HTMLElement

    val title =
        document.getElementById(
            "result-title"
        ) as HTMLElement

    val subtitle =
        document.getElementById(
            "result-sub"
        ) as HTMLElement

    val pointsGained =
        newState.score - oldScore

    if (pointsGained > 0) {

        selectedButton
            .classList
            .add("correct")

        banner.className =
            "result-banner show correct"

        title.textContent =
            "correct!"

        subtitle.textContent =
            "+$pointsGained pts · " +
                    "score: ${newState.score} · " +
                    "streak: ${newState.streak}"

    } else {

        selectedButton
            .classList
            .add("wrong")

        banner.className =
            "result-banner show wrong"

        title.textContent =
            "wrong!"

        subtitle.textContent =
            "Lives remaining: ${newState.lives}"
    }

    revealAlbumArt()

    val nowPlaying =
        document.getElementById(
            "now-playing"
        ) as HTMLElement

    nowPlaying.style.display =
        "none"

    if (!newState.gameOver) {

        val nextButton =
            document.getElementById(
                "next-btn"
            ) as HTMLButtonElement

        nextButton
            .classList
            .add("show")
    }
}

private fun revealAlbumArt() {

    val albumImg =
        document.getElementById(
            "album-img"
        ) as HTMLImageElement

    albumImg.style.transition =
        "filter 0.5s ease"

    albumImg.style.filter =
        "blur(0px)"
}

private fun updateStats(
    state: GameState
) {
    val score =
        document.getElementById(
            "stat-score"
        ) as HTMLElement

    score.textContent =
        state.score.toString()

    val streak =
        document.getElementById(
            "stat-streak"
        ) as HTMLElement

    streak.textContent =
        state.streak.toString()

    val livesContainer =
        document.getElementById(
            "lives-dots"
        ) as HTMLElement

    livesContainer.innerHTML =
        ""

    repeat(3) { index ->

        val dot =
            document.createElement(
                "div"
            ) as HTMLElement

        dot.className =
            if (
                index < state.lives
            ) {
                "life-dot"
            } else {
                "life-dot lost"
            }

        livesContainer.appendChild(
            dot
        )
    }
}

private fun disableChoices() {

    val buttons =
        document.querySelectorAll(
            ".choice-btn"
        )

    for (
    index in
    0 until buttons.length
    ) {
        val button =
            buttons.item(index)
                    as? HTMLButtonElement

        button?.disabled =
            true
    }
}

private fun resetResult() {

    stopTimer()

    val banner =
        document.getElementById(
            "result-banner"
        ) as HTMLElement

    banner.className =
        "result-banner"

    val nextButton =
        document.getElementById(
            "next-btn"
        ) as HTMLButtonElement

    nextButton
        .classList
        .remove("show")

    val container =
        document.getElementById(
            "choices-wrap"
        ) as HTMLElement

    container.innerHTML =
        ""

    val gameError =
        document.getElementById(
            "game-error"
        ) as HTMLElement

    gameError.textContent =
        ""

    val albumImg =
        document.getElementById(
            "album-img"
        ) as HTMLImageElement

    albumImg.style.transition =
        "none"

    albumImg.style.filter =
        "blur(24px)"

    val timerBar =
        document.getElementById(
            "timer-bar"
        ) as HTMLElement

    timerBar.style.transition =
        "none"

    timerBar.style.width =
        "100%"

    timerBar.style.background =
        "var(--green)"

    val nowPlaying =
        document.getElementById(
            "now-playing"
        ) as HTMLElement

    nowPlaying.style.display =
        "none"
}

private fun showGameOver(
    state: GameState
) {
    stopTimer()

    showScreen(
        "screen-gameover"
    )

    val score =
        document.getElementById(
            "final-score"
        ) as HTMLElement

    score.textContent =
        "${state.score}pts"

    val subtitle =
        document.getElementById(
            "final-sub"
        ) as HTMLElement

    subtitle.textContent =
        "Final score"

    val nowPlaying =
        document.getElementById(
            "now-playing"
        ) as HTMLElement

    nowPlaying.style.display =
        "none"

    scope.launch {
        loadLeaderboard()
    }
}

private suspend fun loadLeaderboard() {

    try {
        val scores =
            api.getScores()

        val container =
            document.getElementById(
                "leaderboard-list"
            ) as HTMLElement

        container.innerHTML =
            ""

        if (scores.isEmpty()) {

            container.innerHTML =
                """
                <div style="
                    font-size:13px;
                    color:var(--muted);
                    padding:8px 0
                ">
                    no scores yet
                </div>
                """.trimIndent()

            return
        }

        scores.forEachIndexed {
                index,
                score ->

            val row =
                document.createElement(
                    "div"
                ) as HTMLElement

            row.className =
                "lb-row"

            val rankClass =
                if (index == 0) {
                    "gold"
                } else {
                    ""
                }

            val pointsClass =
                if (index > 0) {
                    "dim"
                } else {
                    ""
                }

            row.innerHTML =
                """
                <div class="lb-rank $rankClass">
                    ${index + 1}
                </div>

                <div class="lb-name">
                    ${score.playerName}
                </div>

                <div class="lb-pts $pointsClass">
                    ${score.score}pts
                </div>
                """.trimIndent()

            container.appendChild(
                row
            )
        }

    } catch (e: Exception) {
        setGameOverError(
            "Could not load leaderboard."
        )
    }
}

private fun showScreen(
    screenId: String
) {
    val screens =
        document.querySelectorAll(
            ".screen"
        )

    for (
    index in
    0 until screens.length
    ) {
        val element =
            screens.item(index)
                    as? HTMLElement

        element
            ?.classList
            ?.remove("active")
    }

    document
        .getElementById(
            screenId
        )
        ?.classList
        ?.add("active")
}

private fun startTimer() {

    stopTimer()

    timeLeft =
        TIME_LIMIT_SECONDS

    val timerBar =
        document.getElementById(
            "timer-bar"
        ) as HTMLElement

    val albumImg =
        document.getElementById(
            "album-img"
        ) as HTMLImageElement

    timerBar.style.transition =
        "none"

    timerBar.style.width =
        "100%"

    timerBar.style.background =
        "var(--green)"

    albumImg.style.transition =
        "none"

    albumImg.style.filter =
        "blur(24px)"

    timerId =
        window.setInterval(
            handler = {

                timeLeft--

                val percentage =
                    (
                            timeLeft.toDouble() /
                                    TIME_LIMIT_SECONDS
                            ) * 100.0

                val elapsedFraction =
                    1.0 -
                            (
                                    timeLeft.toDouble() /
                                            TIME_LIMIT_SECONDS
                                    )

                timerBar.style.transition =
                    "width 1s linear"

                timerBar.style.width =
                    "${
                        percentage
                            .coerceAtLeast(0.0)
                    }%"

                val blur =
                    24.0 *
                            (
                                    1.0 -
                                            elapsedFraction
                                    )

                albumImg.style.transition =
                    "filter 1s linear"

                albumImg.style.filter =
                    "blur(${
                        blur.coerceAtLeast(0.0)
                    }px)"

                timerBar.style.background =
                    when {
                        percentage < 30 ->
                            "var(--red)"

                        percentage < 60 ->
                            "var(--amber)"

                        else ->
                            "var(--green)"
                    }

                if (timeLeft <= 0) {

                    stopTimer()

                    if (!answered) {

                        answered = true

                        disableChoices()

                        scope.launch {

                            val state =
                                gameClient.answer(
                                    ""
                                )

                            showTimeoutResult(
                                state
                            )
                        }
                    }
                }
            },
            timeout = 1000
        )
}

private fun stopTimer() {

    timerId?.let {
        window.clearInterval(
            it
        )
    }

    timerId = null
}

private fun showTimeoutResult(
    state: GameState
) {
    val banner =
        document.getElementById(
            "result-banner"
        ) as HTMLElement

    val title =
        document.getElementById(
            "result-title"
        ) as HTMLElement

    val subtitle =
        document.getElementById(
            "result-sub"
        ) as HTMLElement

    revealAlbumArt()

    banner.className =
        "result-banner show timeout"

    title.textContent =
        "too slow!"

    subtitle.textContent =
        "Lives remaining: ${state.lives}"

    val nowPlaying =
        document.getElementById(
            "now-playing"
        ) as HTMLElement

    nowPlaying.style.display =
        "none"

    if (!state.gameOver) {

        val nextButton =
            document.getElementById(
                "next-btn"
            ) as HTMLButtonElement

        nextButton
            .classList
            .add("show")
    }
}

private fun extractSpotifyPlaylistId(
    input: String
): String? {

    val trimmed =
        input.trim()

    if (trimmed.isEmpty()) {
        return null
    }

    /*
     * Also allow someone to paste
     * the raw Spotify playlist ID.
     */
    if (
        !trimmed.contains("/") &&
        !trimmed.contains("?")
    ) {
        return trimmed
    }

    val marker =
        "/playlist/"

    val markerIndex =
        trimmed.indexOf(
            marker
        )

    if (markerIndex == -1) {
        return null
    }

    val afterPlaylist =
        trimmed.substring(
            markerIndex +
                    marker.length
        )

    return afterPlaylist
        .substringBefore("?")
        .substringBefore("/")
        .takeIf {
            it.isNotBlank()
        }
}

private fun setPlaylistMessage(
    message: String,
    isError: Boolean
) {
    val element =
        document.getElementById(
            "playlist-msg"
        ) as HTMLElement

    element.textContent =
        message

    element.className =
        if (isError) {
            "playlist-msg error"
        } else if (
            message.isNotEmpty()
        ) {
            "playlist-msg ok"
        } else {
            "playlist-msg"
        }
}

private fun setStartError(
    message: String
) {
    val error =
        document.getElementById(
            "start-error"
        ) as HTMLElement

    error.textContent =
        message
}

private fun setGameOverError(
    message: String
) {
    val error =
        document.getElementById(
            "gameover-error"
        ) as HTMLElement

    error.textContent =
        message
}

private suspend fun loadSavedPlaylists() {

    val savedPlaylists =
        document.getElementById(
            "saved-playlists"
        ) as HTMLSelectElement

    val previouslySelectedId =
        selectedPlaylistId

    val playlists =
        try {
            api.getPlaylists()
        } catch (e: Exception) {
            console.error(
                "Failed to load playlists",
                e
            )
            return
        }

    savedPlaylists.innerHTML =
        ""

    val defaultOption =
        document.createElement(
            "option"
        ) as HTMLOptionElement

    defaultOption.value = ""
    defaultOption.textContent =
        "Default playlist"

    savedPlaylists.appendChild(
        defaultOption
    )

    playlists.forEach { playlist ->

        val option =
            document.createElement(
                "option"
            ) as HTMLOptionElement

        option.value =
            playlist.id

        option.textContent =
            playlist.name

        savedPlaylists.appendChild(
            option
        )
    }

    savedPlaylists.value =
        previouslySelectedId ?: ""
}
