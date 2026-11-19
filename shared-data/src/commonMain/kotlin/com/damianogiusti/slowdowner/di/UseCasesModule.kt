package com.damianogiusti.slowdowner.di

import com.damianogiusti.slowdowner.domain.backup.ExportLibraryBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.ExportPlaylistBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.ExportTrackBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.FindPlaylistConflictsUseCase
import com.damianogiusti.slowdowner.domain.backup.RestoreBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.SaveBackupUseCase
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
import com.damianogiusti.slowdowner.domain.player.ObservePlayerPreferencesUseCase
import com.damianogiusti.slowdowner.domain.player.SetLoopFineUseCase
import com.damianogiusti.slowdowner.domain.player.SetPitchFineUseCase
import com.damianogiusti.slowdowner.domain.player.SetWaveformShownUseCase
import com.damianogiusti.slowdowner.domain.playback.CloseTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.HandleEndOfTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.ReadWaveformUseCase
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
import com.damianogiusti.slowdowner.domain.stems.AcknowledgeStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.CancelStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.DeleteStemModelUseCase
import com.damianogiusti.slowdowner.domain.stems.DeleteTrackStemsUseCase
import com.damianogiusti.slowdowner.domain.stems.DownloadStemModelUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveStemModelUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveSeparatedTracksUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveTrackStemsUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.SetStemGainUseCase
import com.damianogiusti.slowdowner.domain.stems.SetTrackModeUseCase
import com.damianogiusti.slowdowner.domain.stems.SwitchTrackModeUseCase
import com.damianogiusti.slowdowner.domain.stems.ModeSwitchRepository
import com.damianogiusti.slowdowner.domain.stems.ObserveModeSwitchUseCase
import com.damianogiusti.slowdowner.data.stems.InMemoryModeSwitchRepository
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.bind
import com.damianogiusti.slowdowner.domain.stems.StartStemSeparationUseCase
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
    factoryOf(::ObservePlayerPreferencesUseCase)
    factoryOf(::SetPitchFineUseCase)
    factoryOf(::SetLoopFineUseCase)
    factoryOf(::SetWaveformShownUseCase)
    factoryOf(::ReadWaveformUseCase)
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
    factoryOf(::ObservePlaylistsUseCase)
    factoryOf(::ObservePlaylistUseCase)
    factoryOf(::ObservePlaylistMembershipUseCase)
    factoryOf(::CreatePlaylistUseCase)
    factoryOf(::RenamePlaylistUseCase)
    factoryOf(::DeletePlaylistUseCase)
    factoryOf(::AddTrackToPlaylistUseCase)
    factoryOf(::RemoveTrackFromPlaylistUseCase)
    factoryOf(::ReorderPlaylistUseCase)
    factoryOf(::ObserveTrackStemsUseCase)
    factoryOf(::ObserveSeparatedTracksUseCase)
    factoryOf(::SetTrackModeUseCase)
    // Here and not on a platform: the player observes it on every platform, stems or not.
    singleOf(::InMemoryModeSwitchRepository) { bind<ModeSwitchRepository>() }
    factoryOf(::ObserveModeSwitchUseCase)
    factoryOf(::SwitchTrackModeUseCase)
    factoryOf(::SetStemGainUseCase)
    factoryOf(::DeleteTrackStemsUseCase)
    factoryOf(::ObserveStemModelUseCase)
    factoryOf(::DownloadStemModelUseCase)
    factoryOf(::DeleteStemModelUseCase)
    factoryOf(::StartStemSeparationUseCase)
    factoryOf(::CancelStemSeparationUseCase)
    factoryOf(::ObserveStemSeparationUseCase)
    factoryOf(::AcknowledgeStemSeparationUseCase)
    // Explicit lambdas: the clock parameter keeps its default.
    factory { ExportTrackBackupUseCase(get()) }
    factory { ExportLibraryBackupUseCase(get()) }
    factory { ExportPlaylistBackupUseCase(get()) }
    factoryOf(::SaveBackupUseCase)
    factoryOf(::FindPlaylistConflictsUseCase)
    factoryOf(::RestoreBackupUseCase)
}
