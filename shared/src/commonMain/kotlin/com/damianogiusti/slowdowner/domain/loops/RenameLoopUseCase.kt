package com.damianogiusti.slowdowner.domain.loops

class RenameLoopUseCase(private val repository: LoopRepository) {
    /** False when the name is empty or longer than [Loop.MAX_NAME_LENGTH]: nothing is saved. */
    suspend operator fun invoke(loopId: Long, name: String): Boolean {
        val normalized = Loop.normalizeName(name) ?: return false
        repository.rename(loopId, normalized)
        return true
    }
}
