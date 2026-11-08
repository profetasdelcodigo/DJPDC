package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDJCoordinatorTest {
    private val library = listOf(
        LocalTrackEntry(1L, "Current", "Artist A", "Album", 210_000L),
        LocalTrackEntry(2L, "Next", "Artist B", "Album", 190_000L),
        LocalTrackEntry(3L, "Other", "Artist C", "Album", 200_000L)
    )

    private fun activeState() = AutoDJSessionState(AutoDJMode.ACTIVE, 1L)

    @Test
    fun cannotAttachTrackOutsideLocalLibrary() {
        assertNull(AutoDJCoordinator.attachCurrentTrack(
            AutoDJSessionState(AutoDJMode.ACTIVE), 99L, library.map { it.id }.toSet()
        ))
    }

    @Test
    fun attachCurrentTrackUpdatesSessionOnlyForLocalId() {
        val updated = AutoDJCoordinator.attachCurrentTrack(
            AutoDJSessionState(AutoDJMode.ACTIVE), 1L, library.map { it.id }.toSet()
        )
        assertNotNull(updated)
        assertEquals(1L, updated?.currentTrackId)
    }

    @Test
    fun inactiveSessionCannotProposeNextTrack() {
        val result = AutoDJCoordinator.proposeNext(
            AutoDJSessionState(AutoDJMode.OFF, 1L), library
        )
        assertTrue(result is AutoDJCoordinatorDecision.Blocked)
    }

    @Test
    fun activeSessionProposesOnlyAnotherLocalTrack() {
        val result = AutoDJCoordinator.proposeNext(activeState(), library)
        assertTrue(result is AutoDJCoordinatorDecision.Ready)
        val ready = result as AutoDJCoordinatorDecision.Ready
        assertTrue(ready.nextTrack.id in library.map { it.id })
        assertFalse(ready.nextTrack.id == 1L)
        assertEquals(DJTransitionStyle.SIMPLE_FADE, ready.plan.style)
    }

    @Test
    fun handoffApprovalRequiresSessionStillActiveAndSameCurrentTrack() {
        val ready = AutoDJCoordinator.proposeNext(activeState(), library)
            as AutoDJCoordinatorDecision.Ready
        val approval = AutoDJTransitionApproval(1L, ready.nextTrack.id, ready.plan)
        val ids = library.map { it.id }.toSet()

        assertTrue(AutoDJCoordinator.approveHandoff(activeState(), approval, ids, ready.nextTrack.durationMs))
        assertFalse(AutoDJCoordinator.approveHandoff(
            AutoDJSessionState(AutoDJMode.PAUSED, 1L), approval, ids, ready.nextTrack.durationMs
        ))
        assertFalse(AutoDJCoordinator.approveHandoff(
            AutoDJSessionState(AutoDJMode.ACTIVE, 3L), approval, ids, ready.nextTrack.durationMs
        ))
    }

    @Test
    fun handoffApprovalFailsIfNextTrackDisappearsFromLibrary() {
        val ready = AutoDJCoordinator.proposeNext(activeState(), library)
            as AutoDJCoordinatorDecision.Ready
        val approval = AutoDJTransitionApproval(1L, ready.nextTrack.id, ready.plan)

        assertFalse(AutoDJCoordinator.approveHandoff(
            activeState(), approval, setOf(1L, 3L), ready.nextTrack.durationMs
        ))
    }

    @Test
    fun handoffApprovalRevalidatesDurationAndPlan() {
        val ready = AutoDJCoordinator.proposeNext(activeState(), library)
            as AutoDJCoordinatorDecision.Ready
        val approval = AutoDJTransitionApproval(1L, ready.nextTrack.id, ready.plan)
        val ids = library.map { it.id }.toSet()

        assertFalse(AutoDJCoordinator.approveHandoff(activeState(), approval, ids, 0L))
    }
}
