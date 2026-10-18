package com.damianogiusti.slowdowner.domain.theme

/** Light or dark, picked in the header's theme menu. Dark unless the user picked light. */
enum class ThemeMode {
    Dark,
    Light,
    ;

    companion object {
        val DEFAULT = Dark
    }
}
