package com.damianogiusti.slowdowner.ui.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.whatsnew.GetWhatsNewUseCase
import com.damianogiusti.slowdowner.domain.whatsnew.RecordWhatsNewShownUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** The popup with the changes, on the first launch after an update. Desktop only, like the support prompt. */
class WhatsNewViewModel(
    getWhatsNew: GetWhatsNewUseCase,
    private val recordWhatsNewShown: RecordWhatsNewShownUseCase,
) : ViewModel() {

    // Once per process: a new subscription (the window back from the Dock) must not open it again.
    private val isClosed = MutableStateFlow(false)
    private var isShownRecorded = false

    val state: StateFlow<WhatsNewUiState> = combine(
        flow { emit(getWhatsNew()) },
        isClosed,
    ) { sections, isClosed ->
        if (sections.isEmpty() || isClosed) return@combine WhatsNewUiState.Hidden
        recordShownOnce(sections.size)
        WhatsNewUiState.Visible(sections.map { WhatsNewSection(it.version.toString(), it.lines) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatsNewUiState.Hidden)

    /** OK, Esc or a click outside. */
    fun onCloseClicked() {
        isClosed.value = true
    }

    private suspend fun recordShownOnce(sectionCount: Int) {
        if (isShownRecorded) return
        isShownRecorded = true
        recordWhatsNewShown(sectionCount)
    }
}
