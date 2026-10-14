package com.damianogiusti.slowdowner.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class PlaylistRoute(val playlistId: Long)

@Serializable
data object PlayerRoute
