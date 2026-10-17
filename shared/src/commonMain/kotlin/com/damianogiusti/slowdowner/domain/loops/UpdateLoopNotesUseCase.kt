package com.damianogiusti.slowdowner.domain.loops

/** Saves the notes of a loop as given. The UI debounces typing: this writes on every call. */
class UpdateLoopNotesUseCase(private val repository: LoopRepository) {
    suspend operator fun invoke(loopId: Long, notes: String) =
        repository.updateNotes(loopId, notes.take(Loop.MAX_NOTES_LENGTH))
}
