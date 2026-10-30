package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion

/** What changed in one version: one plain line each, in English. */
data class ChangelogSection(val version: AppVersion, val lines: List<String>)
