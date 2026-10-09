package com.msegal.tunegame.session

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val id: String
)