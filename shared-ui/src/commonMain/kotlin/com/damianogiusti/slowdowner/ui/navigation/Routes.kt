package com.damianogiusti.slowdowner.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistRoute(val playlistId: Long)

@Serializable
data object PlayerRoute
