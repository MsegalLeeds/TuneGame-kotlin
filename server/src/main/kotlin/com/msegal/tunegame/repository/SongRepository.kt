package com.msegal.tunegame.repository

import com.msegal.tunegame.game.Song

class SongRepository {

    fun loadSongs(): List<Song> {
        val inputStream =
            javaClass.classLoader.getResourceAsStream("music.csv")
                ?: throw IllegalStateException(
                    "music.csv not found"
                )

        return inputStream
            .bufferedReader()
            .useLines { lines ->
                lines
                    .drop(1)
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val columns = line.split(",")

                        Song(
                            song = columns[0].trim(),
                            album = columns[1].trim(),
                            artist = columns[2].trim()
                        )
                    }
                    .toList()
            }
    }
}