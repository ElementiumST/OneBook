package com.onebook.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class BookDto(val id: String, val title: String, val author: String)

@Serializable
data class PositionDto(val positionMs: Long)

@Serializable
data class ErrorDto(val message: String)
