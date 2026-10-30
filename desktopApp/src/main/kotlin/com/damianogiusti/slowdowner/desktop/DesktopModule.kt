package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.backup.BackupArchive
import com.damianogiusti.slowdowner.data.backup.DefaultBackupRepository
import com.damianogiusti.slowdowner.data.database.DatabaseTransactionRunner
import com.damianogiusti.slowdowner.data.database.RoomTransactionRunner
import com.damianogiusti.slowdowner.data.database.SlowDownerDatabase
import com.damianogiusti.slowdowner.data.database.desktopDatabase
import com.damianogiusti.slowdowner.data.effects.DefaultEffectsSettingsRepository
import com.damianogiusti.slowdowner.data.effects.EffectsSettingsStore
import com.damianogiusti.slowdowner.data.effects.PreferencesEffectsSettingsStore
import com.damianogiusti.slowdowner.data.export.DefaultExportRepository
import com.damianogiusti.slowdowner.data.export.OfflineRenderer
import com.damianogiusti.slowdowner.data.library.DefaultLibraryRepository
import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import com.damianogiusti.slowdowner.data.library.FileDocumentDataSource
import com.damianogiusti.slowdowner.data.library.HttpFileDataSource
import com.damianogiusti.slowdowner.data.library.LegacyJsonLibraryIndex
import com.damianogiusti.slowdowner.data.library.LibraryStore
import com.damianogiusti.slowdowner.data.library.MediaContainerDataSource
import com.damianogiusti.slowdowner.data.library.NewPipeDownloaderAdapter
import com.damianogiusti.slowdowner.data.library.NewPipeYouTubeStreamDataSource
import com.damianogiusti.slowdowner.data.library.OkHttpFileDataSource
import com.damianogiusti.slowdowner.data.library.PassthroughContainerDataSource
import com.damianogiusti.slowdowner.data.library.RoomLibraryStore
import com.damianogiusti.slowdowner.data.library.YouTubeStreamDataSource
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.data.playback.NativeOfflineRenderer
import com.damianogiusti.slowdowner.data.playlists.DefaultPlaylistRepository
import com.damianogiusti.slowdowner.data.practice.DefaultPracticeRepository
import com.damianogiusti.slowdowner.data.source.DesktopAudioSourceRepository
import com.damianogiusti.slowdowner.data.support.DefaultSupportPromptRepository
import com.damianogiusti.slowdowner.data.support.PreferencesSupportPromptStore
import com.damianogiusti.slowdowner.data.support.SupportPromptStore
import com.damianogiusti.slowdowner.data.update.DefaultUpdateRepository
import com.damianogiusti.slowdowner.data.update.GitHubReleaseDataSource
import com.damianogiusti.slowdowner.data.update.ReleaseDataSource
import com.damianogiusti.slowdowner.data.update.UpdateInstaller
import com.damianogiusti.slowdowner.data.whatsnew.ChangelogDataSource
import com.damianogiusti.slowdowner.data.whatsnew.DefaultWhatsNewRepository
import com.damianogiusti.slowdowner.data.whatsnew.ResourceChangelogDataSource
import com.damianogiusti.slowdowner.data.whatsnew.WhatsNewStore
import com.damianogiusti.slowdowner.desktop.update.DesktopUpdateInstaller
import com.damianogiusti.slowdowner.desktop.update.UpdateSettings
import com.damianogiusti.slowdowner.domain.backup.BackupRepository
import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.EffectsSettingsRepository
import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.library.LibraryRepository
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playlists.PlaylistRepository
import com.damianogiusti.slowdowner.domain.practice.PracticeRepository
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.support.DismissSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.RecordAppLaunchUseCase
import com.damianogiusti.slowdowner.domain.support.ReportSupportPromptShownUseCase
import com.damianogiusti.slowdowner.domain.support.ReportWhyFreeOpenedUseCase
import com.damianogiusti.slowdowner.domain.support.SupportPromptRepository
import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.update.CheckForUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.InstallUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ObserveAvailableUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ReportUpdatePromptShownUseCase
import com.damianogiusti.slowdowner.domain.update.RestartIntoUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.UpdateRepository
import com.damianogiusti.slowdowner.domain.whatsnew.GetWhatsNewUseCase
import com.damianogiusti.slowdowner.domain.whatsnew.IsWhatsNewDueUseCase
import com.damianogiusti.slowdowner.domain.whatsnew.RecordWhatsNewShownUseCase
import com.damianogiusti.slowdowner.domain.whatsnew.WhatsNewRepository
import com.damianogiusti.slowdowner.logging.ConsoleLogger
import com.damianogiusti.slowdowner.logging.Logger
import com.damianogiusti.slowdowner.ui.support.SupportPromptViewModel
import com.damianogiusti.slowdowner.ui.update.UpdateViewModel
import com.damianogiusti.slowdowner.ui.whatsnew.WhatsNewViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Platform bindings of the desktop app: the counterpart of the Android modules in app/di. Use cases
 * and ViewModels come from `useCasesModule` and `uiModule`.
 */
fun desktopModule(
    directories: AppDirectories,
    engine: NativeEngine,
    effectsStore: EffectsSettingsStore = PreferencesEffectsSettingsStore(),
    supportPromptStore: SupportPromptStore = PreferencesSupportPromptStore(),
    logger: Logger = ConsoleLogger(),
    updates: UpdateSettings = UpdateSettings(),
    whatsNew: WhatsNewSettings = WhatsNewSettings(),
) = module {
    single { directories }
    single { logger }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build() }
    single<SlowDownerDatabase> { desktopDatabase(directories.database) }
    single<DatabaseTransactionRunner> { RoomTransactionRunner(get()) }

    // Playback: the C engine (Bungee) on the default output device.
    single { engine }
    single { DesktopPlaybackEngine(get(), get()) }
    single<PlaybackEngine> { get<DesktopPlaybackEngine>() }
    single<EffectsEngine> { get<DesktopPlaybackEngine>() }
    single<AudioSourceRepository> { DesktopAudioSourceRepository(get(), logger) }
    singleOf(::InMemoryNowPlayingRepository) { bind<NowPlayingRepository>() }

    // Library
    single { get<SlowDownerDatabase>().trackDao() }
    single<LibraryStore> {
        RoomLibraryStore(get(), LegacyJsonLibraryIndex(directories.library, logger), directories.library, logger)
    }
    // NewPipe is initialised by the first YouTube import, off the startup path.
    single<YouTubeStreamDataSource> { NewPipeYouTubeStreamDataSource { NewPipeDownloaderAdapter(get()) } }
    singleOf(::OkHttpFileDataSource) { bind<HttpFileDataSource>() }
    single<DocumentDataSource> { FileDocumentDataSource() }
    single<MediaContainerDataSource> { PassthroughContainerDataSource() }
    single<LibraryRepository> {
        DefaultLibraryRepository(get(), get(), get(), get(), get(), Dispatchers.IO, logger)
    }

    // Effects, export, practice, playlists, backup
    single<EffectsSettingsStore> { effectsStore }
    single<EffectsSettingsRepository> { DefaultEffectsSettingsRepository(get(), Dispatchers.IO) }
    single<OfflineRenderer> { NativeOfflineRenderer(get()) }
    single<ExportRepository> {
        DefaultExportRepository(get(), get(), File(directories.cache, "exports"), Dispatchers.IO, logger)
    }
    single { get<SlowDownerDatabase>().practiceDao() }
    single<PracticeRepository> { DefaultPracticeRepository(get(), Dispatchers.IO) }
    single { get<SlowDownerDatabase>().playlistDao() }
    single<PlaylistRepository> { DefaultPlaylistRepository(get(), Dispatchers.IO) }
    single { get<SlowDownerDatabase>().backupDao() }
    single { BackupArchive() }
    single<BackupRepository> {
        DefaultBackupRepository(
            dao = get(),
            transactions = get(),
            store = get(),
            documents = get(),
            archive = get(),
            backupDir = File(directories.cache, "backups"),
            stagingDir = File(directories.cache, "restore-staging"),
            dispatcher = Dispatchers.IO,
            logger = logger,
        )
    }

    // The "Downstep is free" prompt: here and not in the shared modules, the Android build is not published.
    single<SupportPromptStore> { supportPromptStore }
    single<SupportPromptRepository> { DefaultSupportPromptRepository(get(), Dispatchers.IO) }
    factoryOf(::RecordAppLaunchUseCase)
    // Explicit lambdas: the clock parameter keeps its default.
    factory { ObserveSupportPromptUseCase(get()) }
    factory { DismissSupportPromptUseCase(get(), get()) }
    factoryOf(::ReportSupportPromptShownUseCase)
    factoryOf(::ReportWhyFreeOpenedUseCase)
    viewModelOf(::SupportPromptViewModel)

    // Updates from the public releases: desktop only, like the prompt above.
    single<ReleaseDataSource> { GitHubReleaseDataSource(get(), updates.releasesUrl) }
    single<UpdateInstaller> { DesktopUpdateInstaller(updates.installation, directories.updateWork, logger) }
    single<UpdateRepository> {
        DefaultUpdateRepository(
            dataSource = get(),
            installer = get(),
            installedVersion = updates.installedVersion,
            downloadDir = File(directories.cache, "updates/download"),
            dispatcher = Dispatchers.IO,
            logger = logger,
        )
    }
    factoryOf(::CheckForUpdateUseCase)
    factoryOf(::ObserveAvailableUpdateUseCase)
    factoryOf(::InstallUpdateUseCase)
    factoryOf(::RestartIntoUpdateUseCase)
    factoryOf(::ReportUpdatePromptShownUseCase)
    viewModelOf(::UpdateViewModel)

    // What's new after an update: desktop only, like the prompt and the updates above. GetWhatsNewUseCase
    // is a single: it decides once, and the support prompt and the update dialog ask the same answer.
    val runningVersion = AppVersion.parse(whatsNew.appVersion.orEmpty())
    single<WhatsNewStore> { whatsNew.store }
    single<ChangelogDataSource> { ResourceChangelogDataSource(whatsNew.changelogDir, logger) }
    single<WhatsNewRepository> { DefaultWhatsNewRepository(get(), get(), Dispatchers.IO) }
    single { GetWhatsNewUseCase(get(), runningVersion, whatsNew.hasLaunchHistory) }
    factoryOf(::IsWhatsNewDueUseCase)
    factory { RecordWhatsNewShownUseCase(get(), runningVersion, logger) }
    viewModelOf(::WhatsNewViewModel)
}
