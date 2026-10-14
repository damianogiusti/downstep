package com.damianogiusti.slowdowner.ui

import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.backup_restored_tracks
import com.damianogiusti.slowdowner.ui.resources.import_link_description
import com.damianogiusti.slowdowner.ui.resources.remove_confirm
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The strings came from Android's strings.xml: escapes and placeholders must read the same here.
 * Resources follow the JVM locale, so each test sets it: the result must not depend on the machine.
 */
class StringResourcesTest {

    private lateinit var systemLocale: Locale

    @BeforeTest
    fun saveLocale() {
        systemLocale = Locale.getDefault()
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `escaped quotes and placeholders are resolved`() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals(
            "Remove \"Autumn Leaves\" from the library? The file will be deleted.",
            getString(Res.string.remove_confirm, "Autumn Leaves"),
        )
    }

    @Test
    fun `escaped apostrophes are resolved`() = runTest {
        Locale.setDefault(Locale.ITALIAN)
        assertEquals("Incolla un link: scarico solo l'audio", getString(Res.string.import_link_description))
    }

    @Test
    fun `plurals pick the English form`() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Restored 1 track", getPluralString(Res.plurals.backup_restored_tracks, 1, 1))
        assertEquals("Restored 3 tracks", getPluralString(Res.plurals.backup_restored_tracks, 3, 3))
    }

    @Test
    fun `plurals pick the Italian form`() = runTest {
        Locale.setDefault(Locale.ITALIAN)
        assertEquals("Ripristinato 1 brano", getPluralString(Res.plurals.backup_restored_tracks, 1, 1))
        assertEquals("Ripristinati 3 brani", getPluralString(Res.plurals.backup_restored_tracks, 3, 3))
    }

    @Test
    fun `an unsupported language falls back to English`() = runTest {
        Locale.setDefault(Locale.GERMAN)
        assertEquals("Restored 1 track", getPluralString(Res.plurals.backup_restored_tracks, 1, 1))
    }
}
