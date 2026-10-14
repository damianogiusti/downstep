package com.damianogiusti.slowdowner.domain.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A release newer than the running app, or null. */
class ObserveAvailableUpdateUseCase(private val repository: UpdateRepository) {
    operator fun invoke(): Flow<AvailableUpdate?> = repository.observeLatestRelease().map { latest ->
        val installed = repository.installedVersion() ?: return@map null
        latest?.takeIf { it.version > installed }?.let { AvailableUpdate(installed, it) }
    }
}
