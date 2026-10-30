package com.damianogiusti.slowdowner.domain.update

/**
 * The user read that the app closes and opens again by itself, and confirmed. True: quit now, the new
 * version opens. False: the swap cannot start, and the app keeps running as it is.
 */
class RestartIntoUpdateUseCase(private val repository: UpdateRepository) {
    suspend operator fun invoke(): Boolean = repository.startSwap()
}
