package com.onebook.core.domain

data class Book(val id: String, val title: String, val author: String)

data class PlaybackSource(val uri: String)

enum class PlaybackFailure { OpenBook, PlayAudio, SavePosition }

data class PlayerState(
    val book: Book? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val failure: PlaybackFailure? = null,
)
