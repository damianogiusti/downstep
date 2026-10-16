package com.damianogiusti.slowdowner.di

import com.damianogiusti.slowdowner.ui.backup.BackupUseCases
import com.damianogiusti.slowdowner.ui.backup.BackupViewModel
import com.damianogiusti.slowdowner.ui.effects.EffectsViewModel
import com.damianogiusti.slowdowner.ui.library.ImportViewModel
import com.damianogiusti.slowdowner.ui.library.LibraryViewModel
import com.damianogiusti.slowdowner.ui.player.ExportUseCases
import com.damianogiusti.slowdowner.ui.player.LoopUseCases
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.player.PracticeSeams
import com.damianogiusti.slowdowner.ui.player.PracticeUseCases
import com.damianogiusti.slowdowner.ui.playlists.AddToPlaylistViewModel
import com.damianogiusti.slowdowner.ui.playlists.PlaylistEditUseCases
import com.damianogiusti.slowdowner.ui.playlists.PlaylistTrackUseCases
import com.damianogiusti.slowdowner.ui.playlists.PlaylistViewModel
import com.damianogiusti.slowdowner.ui.theme.ThemeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of the shared screens, for both apps. */
val uiModule = module {
    // Use-case groups that keep ViewModel constructors short.
    factoryOf(::PracticeUseCases)
    factoryOf(::LoopUseCases)
    factoryOf(::PracticeSeams)
    factoryOf(::ExportUseCases)
    factoryOf(::PlaylistEditUseCases)
    factoryOf(::PlaylistTrackUseCases)
    factoryOf(::BackupUseCases)

    // Explicit lambda: the background dispatcher keeps its default.
    viewModel { PlayerViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::ImportViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::LibraryViewModel)
    viewModelOf(::PlaylistViewModel)
    viewModelOf(::AddToPlaylistViewModel)
    viewModelOf(::EffectsViewModel)
    viewModelOf(::ThemeViewModel)
}
