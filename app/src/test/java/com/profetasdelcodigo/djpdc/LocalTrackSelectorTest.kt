package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTrackSelectorTest {
    private val library = listOf(
        LocalTrackEntry(1, "Noche de Fiesta", "Artista Uno", "Verano", 210_000),
        LocalTrackEntry(2, "Balada de Amor", "Artista Dos", "Románticas", 230_000),
        LocalTrackEntry(3, "Fiesta Total", "Artista Dos", "En Vivo", 190_000),
        LocalTrackEntry(4, "Instrumental", "Orquesta", "Colección", 180_000)
    )

    @Test
    fun queryRanksOnlyTracksMatchingLocalMetadata() {
        val result = LocalTrackSelector.rank(
            library,
            LocalTrackSelectionRequest(query = "fiesta")
        )
        assertEquals(listOf(3L, 1L), result.map { it.track.id })
    }

    @Test
    fun allowedIdsRestrictSelectionToPlaylist() {
        val result = LocalTrackSelector.rank(
            library,
            LocalTrackSelectionRequest(allowedTrackIds = setOf(2L, 4L))
        )
        assertEquals(setOf(2L, 4L), result.map { it.track.id }.toSet())
    }

    @Test
    fun favoritesAreOnlyAPreferenceNotAnExternalSource() {
        val result = LocalTrackSelector.rank(
            library,
            LocalTrackSelectionRequest(
                query = "artista dos",
                favoriteTrackIds = setOf(3L)
            )
        )
        assertEquals(3L, result.first().track.id)
    }

    @Test
    fun excludesCurrentTrackAndDeduplicatesIds() {
        val result = LocalTrackSelector.rank(
            library + library.first(),
            LocalTrackSelectionRequest(excludedTrackIds = setOf(1L))
        )
        assertEquals(listOf(2L, 3L, 4L), result.map { it.track.id })
    }

    @Test
    fun emptyQueryReturnsLocalLibraryInDeterministicOrderAndRespectsLimit() {
        val result = LocalTrackSelector.rank(
            library,
            LocalTrackSelectionRequest(limit = 2)
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.track.id in library.map(LocalTrackEntry::id) })
    }

    @Test
    fun unmatchedQueryReturnsNoInventedTracks() {
        val result = LocalTrackSelector.rank(
            library,
            LocalTrackSelectionRequest(query = "cumbia")
        )
        assertTrue(result.isEmpty())
    }
}
