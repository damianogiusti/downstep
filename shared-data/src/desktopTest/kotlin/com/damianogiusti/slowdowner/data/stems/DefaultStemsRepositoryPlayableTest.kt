package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.PlayableStem
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.fake.FakeStemsStore
import com.damianogiusti.slowdowner.fake.FakeStemsStore.Companion.stem
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStemsRepositoryPlayableTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val logger = RecordingLogger()

    @Test
    fun `when the files are on disk then the playable stems are the folder plus the file name with their gains`() = runTest {
        val root = folder.newFolder()
        // Stored out of order on purpose: the playable order is the one of StemKind.
        val store = FakeStemsStore(
            listOf(TrackStemsDto("a", "model", "STEMS", listOf(stem("DRUMS", 0.5f), stem("VOCALS")))),
            root,
        )
        files(store, "a", "vocals.wav", "drums.wav")

        val playable = repository(store).playableStems("a")

        assertEquals(TrackMode.STEMS, playable?.mode)
        assertEquals(
            listOf(
                PlayableStem(StemKind.VOCALS, File(root, "a/vocals.wav").path, 1f),
                PlayableStem(StemKind.DRUMS, File(root, "a/drums.wav").path, 0.5f),
            ),
            playable?.stems,
        )
    }

    @Test
    fun `when stems are muted or soloed then the playable stems carry the flags`() = runTest {
        val root = folder.newFolder()
        val store = FakeStemsStore(
            listOf(
                TrackStemsDto(
                    "a", "model", "STEMS",
                    listOf(StemFileDto("VOCALS", "vocals.wav", 0.4f, isMuted = true), StemFileDto("DRUMS", "drums.wav", 1f, isSoloed = true)),
                ),
            ),
            root,
        )
        files(store, "a", "vocals.wav", "drums.wav")

        val playable = repository(store).playableStems("a")

        assertEquals(
            listOf(
                PlayableStem(StemKind.VOCALS, File(root, "a/vocals.wav").path, 0.4f, isMuted = true),
                PlayableStem(StemKind.DRUMS, File(root, "a/drums.wav").path, 1f, isSoloed = true),
            ),
            playable?.stems,
        )
        assertEquals(0f, playable?.mix?.effectiveGain(StemKind.VOCALS))
    }

    @Test
    fun `when the track is in full track mode then the stems are still playable with that mode`() = runTest {
        val store = FakeStemsStore(listOf(TrackStemsDto("a", "model", "FULL_TRACK", listOf(stem("VOCALS")))), folder.newFolder())
        files(store, "a", "vocals.wav")

        assertEquals(TrackMode.FULL_TRACK, repository(store).playableStems("a")?.mode)
    }

    @Test
    fun `when a stem file is missing then the track is not playable and it is logged once`() = runTest {
        val store = FakeStemsStore(
            listOf(TrackStemsDto("a", "model", "STEMS", listOf(stem("VOCALS"), stem("DRUMS")))),
            folder.newFolder(),
        )
        files(store, "a", "vocals.wav")
        val repository = repository(store)

        assertNull(repository.playableStems("a"))
        assertNull(repository.playableStems("a"))

        assertEquals(1, logger.at('W').size)
        assertFalse(logger.at('W').single().message.contains("vocals"))
    }

    @Test
    fun `when the track is not separated then there are no playable stems`() = runTest {
        assertNull(repository(FakeStemsStore(root = folder.newFolder())).playableStems("a"))
    }

    @Test
    fun `when the stored mode is unknown then there are no playable stems`() = runTest {
        val store = FakeStemsStore(listOf(TrackStemsDto("a", "model", "HOLOGRAM", listOf(stem("VOCALS")))), folder.newFolder())
        files(store, "a", "vocals.wav")

        assertNull(repository(store).playableStems("a"))
    }

    private fun repository(store: FakeStemsStore) = DefaultStemsRepository(store, UnconfinedTestDispatcher(), logger)

    private fun files(store: FakeStemsStore, trackId: String, vararg names: String) {
        for (name in names) File(store.dirFor(trackId), name).writeText("x")
    }
}
