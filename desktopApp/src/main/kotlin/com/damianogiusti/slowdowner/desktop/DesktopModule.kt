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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.schabi.newpipe.extractor.NewPipe
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
) = module {
    single { directories }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build() }
    single<SlowDownerDatabase> { desktopDatabase(directories.database) }
    single<DatabaseTransactionRunner> { RoomTransactionRunner(get()) }
    // Must be initialised once before any YouTube import.
    single(createdAtStart = true) { NewPipeDownloaderAdapter(get()).also(NewPipe::init) }

    // Playback: the C engine (Bungee) on the default output device.
    single { engine }
    single { DesktopPlaybackEngine(get(), get()) }
    single<PlaybackEngine> { get<DesktopPlaybackEngine>() }
    single<EffectsEngine> { get<DesktopPlaybackEngine>() }
    singleOf(::DesktopAudioSourceRepository) { bind<AudioSourceRepository>() }
    singleOf(::InMemoryNowPlayingRepository) { bind<NowPlayingRepository>() }

    // Library
    single { get<SlowDownerDatabase>().trackDao() }
    single<LibraryStore> {
        RoomLibraryStore(get(), LegacyJsonLibraryIndex(directories.library), directories.library)
    }
    singleOf(::NewPipeYouTubeStreamDataSource) { bind<YouTubeStreamDataSource>() }
    singleOf(::OkHttpFileDataSource) { bind<HttpFileDataSource>() }
    single<DocumentDataSource> { FileDocumentDataSource() }
    single<MediaContainerDataSource> { PassthroughContainerDataSource() }
    single<LibraryRepository> {
        DefaultLibraryRepository(
            get(), get(), get(), get(), get(), Dispatchers.IO,
            reportFailure = CrashReporting::reportImportFailure,
        )
    }

    // Effects, export, practice, playlists, backup
    single<EffectsSettingsStore> { effectsStore }
    single<EffectsSettingsRepository> { DefaultEffectsSettingsRepository(get(), Dispatchers.IO) }
    single<OfflineRenderer> { NativeOfflineRenderer(get()) }
    single<ExportRepository> {
        DefaultExportRepository(get(), get(), File(directories.cache, "exports"), Dispatchers.IO)
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
        )
    }
}
