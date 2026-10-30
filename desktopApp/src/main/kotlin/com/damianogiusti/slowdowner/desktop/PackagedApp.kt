package com.damianogiusti.slowdowner.desktop

/**
 * True in the app made by jpackage. A Gradle run is not installed anywhere and has the default
 * version: it must not check for updates, and it must not touch the version stored for the popup.
 */
internal fun isPackagedApp(): Boolean = System.getProperty("jpackage.app-path") != null
