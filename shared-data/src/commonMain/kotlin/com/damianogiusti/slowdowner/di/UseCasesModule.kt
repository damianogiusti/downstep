package com.damianogiusti.slowdowner.di

import com.damianogiusti.slowdowner.domain.analysis.AnalyzeSectionsUseCase
import com.damianogiusti.slowdowner.domain.backup.ExportLibraryBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.ExportPlaylistBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.ExportTrackBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.FindPlaylistConflictsUseCase
import com.damianogiusti.slowdowner.domain.backup.RestoreBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.SaveBackupUseCase
import com.damianogiusti.slowdowner.domain.click.SetCountInUseCase
import com.damianogiusti.slowdowner.domain.click.SetMetronomeUseCase
import com.damianogiusti.slowdowner.domain.effects.ApplyEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.ObserveEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.RestoreEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.UpdateEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.FindImportableUrlUseCase
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.library.ImportTrackFromUrlUseCase
import com.damianogiusti.slowdowner.domain.library.ObserveLibraryUseCase
import com.damianogiusti.slowdowner.domain.library.RemoveTrackUseCase
import com.damianogiusti.slowdowner.domain.loops.CreateLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.DeleteLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.ObserveLoopsUseCase
import com.damianogiusti.slowdowner.domain.loops.RecordLoopPassUseCase
import com.damianogiusti.slowdowner.domain.loops.RenameLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.ResetLoopPassesUseCase
import com.damianogiusti.slowdowner.domain.loops.SetActiveLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.SetSnapToBeatUseCase
import com.damianogiusti.slowdowner.domain.loops.SetTrackBpmUseCase
import com.damianogiusti.slowdowner.domain.loops.UpdateLoopNotesUseCase
import com.damianogiusti.slowdowner.domain.loops.UpdateLoopProgressionUseCase
import com.damianogiusti.slowdowner.domain.loops.UpdateLoopRegionUseCase
import com.damianogiusti.slowdowner.domain.playback.CloseTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.HandleEndOfTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.playlists.AddTrackToPlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.CreatePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.DeletePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistMembershipUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistsUseCase
import com.damianogiusti.slowdowner.domain.playlists.RemoveTrackFromPlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.RenamePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ReorderPlaylistUseCase
import com.damianogiusti.slowdowner.domain.practice.ApplyPresetUseCase
import com.damianogiusti.slowdowner.domain.practice.AutoSavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.practice.DeletePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.ObservePracticeStatesUseCase
import com.damianogiusti.slowdowner.domain.practice.ObservePresetsUseCase
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.practice.SavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.SetLoopRegionUseCase
import com.damianogiusti.slowdowner.domain.practice.StepPitchUseCase
import com.damianogiusti.slowdowner.domain.practice.StepSpeedUseCase
import com.damianogiusti.slowdowner.domain.theme.ObserveThemeModeUseCase
import com.damianogiusti.slowdowner.domain.theme.SetThemeModeUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Every use case, for both apps. They only need the domain interfaces (repositories, engines), which
 * each platform binds in its own modules.
 */
val useCasesModule = module {
    factoryOf(::ObserveLibraryUseCase)
    factoryOf(::RemoveTrackUseCase)
    factoryOf(::FindImportableUrlUseCase)
    factoryOf(::ImportTrackFromUrlUseCase)
    factoryOf(::ImportDocumentUseCase)
    factoryOf(::OpenTrackUseCase)
    factoryOf(::CloseTrackUseCase)
    factoryOf(::ObserveNowPlayingUseCase)
    factoryOf(::HandleEndOfTrackUseCase)
    factoryOf(::ApplyEffectsSettingsUseCase)
    factoryOf(::ObserveEffectsSettingsUseCase)
    factoryOf(::UpdateEffectsSettingsUseCase)
    factoryOf(::RestoreEffectsSettingsUseCase)
    factoryOf(::ExportTrackUseCase)
    factoryOf(::SaveExportUseCase)
    factoryOf(::SavePracticeStateUseCase)
    factoryOf(::OpenTrackForPracticeUseCase)
    factoryOf(::AutoSavePracticeStateUseCase)
    factoryOf(::StepSpeedUseCase)
    factoryOf(::StepPitchUseCase)
    factoryOf(::SetLoopRegionUseCase)
    factoryOf(::ObservePresetsUseCase)
    factoryOf(::ObservePracticeStatesUseCase)
    factoryOf(::SavePresetUseCase)
    factoryOf(::ApplyPresetUseCase)
    factoryOf(::DeletePresetUseCase)
    factoryOf(::ObserveLoopsUseCase)
    factoryOf(::SetActiveLoopUseCase)
    factoryOf(::CreateLoopUseCase)
    factoryOf(::RenameLoopUseCase)
    factoryOf(::UpdateLoopNotesUseCase)
    factoryOf(::UpdateLoopRegionUseCase)
    factoryOf(::DeleteLoopUseCase)
    factoryOf(::RecordLoopPassUseCase)
    factoryOf(::ResetLoopPassesUseCase)
    factoryOf(::UpdateLoopProgressionUseCase)
    factoryOf(::SetTrackBpmUseCase)
    factoryOf(::SetSnapToBeatUseCase)
    // Optional backends: no platform binds them yet, so these get null and report unavailable.
    factory { AnalyzeSectionsUseCase(getOrNull()) }
    factory { SetMetronomeUseCase(getOrNull()) }
    factory { SetCountInUseCase(getOrNull()) }
    factoryOf(::ObserveThemeModeUseCase)
    factoryOf(::SetThemeModeUseCase)
    factoryOf(::ObservePlaylistsUseCase)
    factoryOf(::ObservePlaylistUseCase)
    factoryOf(::ObservePlaylistMembershipUseCase)
    factoryOf(::CreatePlaylistUseCase)
    factoryOf(::RenamePlaylistUseCase)
    factoryOf(::DeletePlaylistUseCase)
    factoryOf(::AddTrackToPlaylistUseCase)
    factoryOf(::RemoveTrackFromPlaylistUseCase)
    factoryOf(::ReorderPlaylistUseCase)
    // Explicit lambdas: the clock parameter keeps its default.
    factory { ExportTrackBackupUseCase(get()) }
    factory { ExportLibraryBackupUseCase(get()) }
    factory { ExportPlaylistBackupUseCase(get()) }
    factoryOf(::SaveBackupUseCase)
    factoryOf(::FindPlaylistConflictsUseCase)
    factoryOf(::RestoreBackupUseCase)
}
