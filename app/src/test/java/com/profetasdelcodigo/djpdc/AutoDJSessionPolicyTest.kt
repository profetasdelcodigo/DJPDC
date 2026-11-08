package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDJSessionPolicyTest {
    @Test
    fun autoDjStartsOffAndCannotPlanWithoutExplicitEnable() {
        val state = AutoDJSessionState(currentTrackId = 10L)
        assertFalse(AutoDJSessionPolicy.mayRequestNextPlan(state, currentTrackIsLocal = true))
    }

    @Test
    fun explicitEnableAllowsPlanningOnlyForKnownLocalCurrentTrack() {
        val state = (AutoDJSessionPolicy.apply(
            AutoDJSessionState(currentTrackId = 10L), AutoDJUserCommand.ENABLE
        ) as AutoDJGateDecision.Updated).state

        assertTrue(AutoDJSessionPolicy.mayRequestNextPlan(state, currentTrackIsLocal = true))
        assertFalse(AutoDJSessionPolicy.mayRequestNextPlan(state, currentTrackIsLocal = false))
    }

    @Test
    fun pauseImmediatelyBlocksPlanningAndExecution() {
        val active = AutoDJSessionState(AutoDJMode.ACTIVE, 10L)
        val paused = (AutoDJSessionPolicy.apply(active, AutoDJUserCommand.PAUSE)
            as AutoDJGateDecision.Updated).state

        assertFalse(AutoDJSessionPolicy.mayRequestNextPlan(paused, true))
        assertFalse(AutoDJSessionPolicy.mayExecuteAcceptedPlan(paused, true, true))
    }

    @Test
    fun disableImmediatelyTurnsOffAndClearsSessionTrack() {
        val active = AutoDJSessionState(AutoDJMode.ACTIVE, 10L)
        val stopped = (AutoDJSessionPolicy.apply(active, AutoDJUserCommand.DISABLE)
            as AutoDJGateDecision.Updated).state

        assertTrue(stopped.mode == AutoDJMode.OFF)
        assertTrue(stopped.currentTrackId == null)
        assertFalse(AutoDJSessionPolicy.mayRequestNextPlan(stopped, true))
    }

    @Test
    fun rejectedPlanCannotBeExecuted() {
        val active = AutoDJSessionState(AutoDJMode.ACTIVE, 10L)
        assertFalse(AutoDJSessionPolicy.mayExecuteAcceptedPlan(active, true, false))
    }

    @Test
    fun resumeRequiresPausedState() {
        val off = AutoDJSessionState()
        assertTrue(AutoDJSessionPolicy.apply(off, AutoDJUserCommand.RESUME) is AutoDJGateDecision.Blocked)
        val paused = AutoDJSessionState(AutoDJMode.PAUSED, 10L)
        val resumed = AutoDJSessionPolicy.apply(paused, AutoDJUserCommand.RESUME) as AutoDJGateDecision.Updated
        assertTrue(resumed.state.mode == AutoDJMode.ACTIVE)
    }
}
