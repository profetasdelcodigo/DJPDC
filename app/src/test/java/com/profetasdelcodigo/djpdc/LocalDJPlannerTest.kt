package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDJPlannerTest {
    private val library = listOf(
        LocalTrackEntry(1, "Current Song", "Artist A", "Album A", 200_000L),
        LocalTrackEntry(2, "Dance Tonight", "Artist B", "Album B", 210_000L),
        LocalTrackEntry(3, "Quiet Morning", "Artist C", "Album C", 220_000L)
    )

    @Test
    fun proposesOnlyAnotherTrackFromLocalLibrary() {
        val result = LocalDJPlanner.proposeNext(
            library, 1L, LocalTrackSelectionRequest(query = "dance"), true
        )
        assertTrue(result is LocalDJDecision.Ready)
        val ready = result as LocalDJDecision.Ready
        assertEquals(2L, ready.nextTrack.id)
        assertTrue(library.any { it.id == ready.transition.nextTrackId })
    }

    @Test
    fun neverProposesCurrentTrack() {
        val result = LocalDJPlanner.proposeNext(
            library, 1L, LocalTrackSelectionRequest(), true
        )
        assertTrue(result is LocalDJDecision.Ready)
        assertTrue((result as LocalDJDecision.Ready).nextTrack.id != 1L)
    }

    @Test
    fun disabledAutoDjCannotCreateAPlan() {
        val result = LocalDJPlanner.proposeNext(
            library, 1L, LocalTrackSelectionRequest(), false
        )
        assertTrue(result is LocalDJDecision.Disabled)
    }

    @Test
    fun noMatchingLocalTrackReturnsNoCandidate() {
        val result = LocalDJPlanner.proposeNext(
            library, 1L, LocalTrackSelectionRequest(query = "cumbia"), true
        )
        assertTrue(result is LocalDJDecision.NoCandidate)
    }

    @Test
    fun neverSelectsOutsideAnExplicitPlaylistScope() {
        val result = LocalDJPlanner.proposeNext(
            library, 1L,
            LocalTrackSelectionRequest(allowedTrackIds = setOf(3L)),
            true
        )
        assertTrue(result is LocalDJDecision.Ready)
        assertEquals(3L, (result as LocalDJDecision.Ready).nextTrack.id)
    }

    @Test
    fun unknownCurrentTrackIsRejected() {
        val result = LocalDJPlanner.proposeNext(
            library, 999L, LocalTrackSelectionRequest(), true
        )
        assertTrue(result is LocalDJDecision.NoCandidate)
    }

    @Test
    fun transitionProposalUsesConservativeSimpleFade() {
        val result = LocalDJPlanner.proposeNext(
            library, 1L, LocalTrackSelectionRequest(), true
        ) as LocalDJDecision.Ready
        assertEquals(DJTransitionStyle.SIMPLE_FADE, result.transition.style)
        assertEquals(1f, result.transition.playbackSpeedRatio)
        assertEquals(0L, result.transition.nextTrackStartMs)
    }
}
