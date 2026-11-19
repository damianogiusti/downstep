package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.data.database.desktopDatabase
import com.damianogiusti.slowdowner.data.database.isConstraintViolation
import com.damianogiusti.slowdowner.data.library.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real Room + bundled SQLite stack: the SQL of the stem tables must behave as the fakes do. */
class StemsDaoTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val database by lazy { desktopDatabase(folder.root.resolve("test.db")) }
    private val dao get() = database.stemsDao()

    @After
    fun tearDown() = database.close()

    private suspend fun track(id: String) = database.trackDao().upsert(TrackEntity(id, id, "/$id.m4a", null, 1))

    private fun state(id: String) = TrackStemStateEntity(id, "model", "STEMS")
    private fun stems(id: String) = listOf(TrackStemEntity(id, "VOCALS", "v.wav", 1f), TrackStemEntity(id, "DRUMS", "d.wav", 0.5f))

    @Test
    fun `when a set is replaced then it is read as one relation`() = runTest {
        track("a")
        dao.replace(state("a"), stems("a"))

        val relation = dao.observe("a").first()

        assertEquals(state("a"), relation?.state)
        assertEquals(stems("a").toSet(), relation?.stems?.toSet())
    }

    @Test
    fun `when the track is not in the library then replace fails and writes nothing`() = runTest {
        try {
            dao.replace(state("ghost"), stems("ghost"))
            fail("saved stems of a track that does not exist")
        } catch (e: Exception) {
            assertTrue("not recognised: $e", isConstraintViolation(e))
        }
        assertNull(dao.observe("ghost").first())
    }

    @Test
    fun `when mode and gain are updated then only that row changes`() = runTest {
        track("a")
        dao.replace(state("a"), stems("a"))

        dao.updateMode("a", "FULL_TRACK")
        dao.updateGain("a", "DRUMS", 0.9f)

        val relation = dao.observe("a").first()
        assertEquals("FULL_TRACK", relation?.state?.mode)
        assertEquals(mapOf("VOCALS" to 1f, "DRUMS" to 0.9f), relation?.stems?.associate { it.stem to it.gain })
    }

    @Test
    fun `when the track is absent then updates change nothing`() = runTest {
        dao.updateMode("ghost", "FULL_TRACK")
        dao.updateGain("ghost", "DRUMS", 0.9f)

        assertNull(dao.observe("ghost").first())
        assertEquals(emptyList<String>(), dao.observeSeparatedTrackIds().first())
    }

    @Test
    fun `when stems are replaced then the earlier ones are gone`() = runTest {
        track("a")
        dao.replace(state("a"), stems("a"))

        dao.replace(state("a").copy(model = "new"), listOf(TrackStemEntity("a", "BASS", "b.wav", 1f)))

        assertEquals(listOf("BASS"), dao.findStems("a").map { it.stem })
        assertEquals("new", dao.observe("a").first()?.state?.model)
    }
}
