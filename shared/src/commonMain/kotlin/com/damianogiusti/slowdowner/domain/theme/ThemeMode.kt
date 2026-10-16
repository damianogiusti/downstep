package com.damianogiusti.slowdowner.domain.theme

/** The app's colour scheme, chosen with the header toggle. Dark unless the user picked light. */
enum class ThemeMode {
    Dark,
    Light,
    ;

    companion object {
        val DEFAULT = Dark
    }
}
