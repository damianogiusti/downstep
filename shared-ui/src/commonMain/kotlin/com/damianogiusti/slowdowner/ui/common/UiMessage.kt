package com.damianogiusti.slowdowner.ui.common

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

data class UiMessage(val text: StringResource, val args: List<Any> = emptyList())

/** A [UiMessage] argument that needs a plural form, e.g. "1 track" or "3 tracks". */
data class UiPlural(val id: PluralStringResource, val count: Int)
