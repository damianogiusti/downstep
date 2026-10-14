package com.damianogiusti.slowdowner.domain.backup

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Local date for backup file names. */
internal fun localToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
