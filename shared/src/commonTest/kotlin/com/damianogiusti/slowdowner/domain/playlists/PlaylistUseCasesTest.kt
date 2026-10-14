package com.damianogiusti.slowdowner.domain.playlists

import com.damianogiusti.slowdowner.fake.FakePlaylistRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

class PlaylistUseCasesTest {

    private val repository = FakePlaylistRepository()

    @Test
    fun `when created with spaces around the name then the name is trimmed`() = runTest {
        val result = CreatePlaylistUseCase(repository)("  Jazz standards ")

        assertEquals(PlaylistResult.Done(1), result)
        assertEquals("Jazz standards", repository.playlists.value.single().name)
    }

    @Test
    fun `when created with a blank name then nothing is created`() = runTest {
        assertEquals(PlaylistResult.InvalidName, CreatePlaylistUseCase(repository)("   "))
        assertEquals(emptyList<Playlist>(), repository.playlists.value)
    }

    @Test
    fun `when renamed with a too long name then the old name stays`() = runTest {
        CreatePlaylistUseCase(repository)("Jazz")

        val result = RenamePlaylistUseCase(repository)(1, "x".repeat(PlaylistName.MAX_LENGTH + 1))

        assertEquals(PlaylistResult.InvalidName, result)
        assertEquals("Jazz", repository.playlists.value.single().name)
    }

    @Test
    fun `when renamed then the new trimmed name is stored`() = runTest {
        CreatePlaylistUseCase(repository)("Jazz")

        RenamePlaylistUseCase(repository)(1, " Bebop ")

        assertEquals("Bebop", repository.playlists.value.single().name)
    }
}
