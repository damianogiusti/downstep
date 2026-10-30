package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.ui.backup.BackupViewModel
import com.damianogiusti.slowdowner.ui.effects.EffectsViewModel
import com.damianogiusti.slowdowner.ui.library.ImportViewModel
import com.damianogiusti.slowdowner.ui.library.LibraryViewModel
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.playlists.AddToPlaylistViewModel
import com.damianogiusti.slowdowner.ui.support.SupportPromptViewModel
import com.damianogiusti.slowdowner.ui.update.UpdateViewModel
import com.damianogiusti.slowdowner.ui.whatsnew.WhatsNewViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Every shared ViewModel resolves against the desktop bindings: wiring errors show up here, not at launch.
 * PlaylistViewModel is left out: it needs the navigation's SavedStateHandle, its use cases are covered.
 */
class DesktopModuleTest {

    private val directories = AppDirectories(Files.createTempDirectory("slowdowner-desktop").toFile())
    private val koin = startKoin {
        modules(
            desktopModule(
                directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), InMemorySupportPromptStore(),
                whatsNew = WhatsNewSettings(store = InMemoryWhatsNewStore()),
            ),
            useCasesModule,
            uiModule,
        )
    }.koin

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    @Test
    fun `every shared ViewModel resolves`() {
        assertNotNull(koin.get<PlayerViewModel>())
        assertNotNull(koin.get<ImportViewModel>())
        assertNotNull(koin.get<BackupViewModel>())
        assertNotNull(koin.get<LibraryViewModel>())
        assertNotNull(koin.get<EffectsViewModel>())
        assertNotNull(koin.get<AddToPlaylistViewModel>())
        assertNotNull(koin.get<SupportPromptViewModel>())
        assertNotNull(koin.get<UpdateViewModel>())
        assertNotNull(koin.get<WhatsNewViewModel>())
    }
}
