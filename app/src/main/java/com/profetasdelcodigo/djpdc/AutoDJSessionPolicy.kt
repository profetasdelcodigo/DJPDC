package com.profetasdelcodigo.djpdc

/**
 * Explicit-consent state gate for the future Auto DJ playback coordinator.
 * This class has no player access and cannot start audio by itself.
 */
enum class AutoDJMode {
    OFF,
    ACTIVE,
    PAUSED
}

enum class AutoDJUserCommand {
    ENABLE,
    DISABLE,
    PAUSE,
    RESUME
}

data class AutoDJSessionState(
    val mode: AutoDJMode = AutoDJMode.OFF,
    val currentTrackId: Long? = null
)

sealed class AutoDJGateDecision {
    data class Updated(val state: AutoDJSessionState) : AutoDJGateDecision()
    data class Blocked(val state: AutoDJSessionState, val reason: String) : AutoDJGateDecision()
}

object AutoDJSessionPolicy {
    fun apply(
        state: AutoDJSessionState,
        command: AutoDJUserCommand
    ): AutoDJGateDecision {
        return when (command) {
            AutoDJUserCommand.ENABLE ->
                AutoDJGateDecision.Updated(state.copy(mode = AutoDJMode.ACTIVE))
            AutoDJUserCommand.DISABLE ->
                AutoDJGateDecision.Updated(AutoDJSessionState())
            AutoDJUserCommand.PAUSE ->
                if (state.mode == AutoDJMode.OFF) {
                    AutoDJGateDecision.Blocked(state, "Auto DJ is already disabled.")
                } else {
                    AutoDJGateDecision.Updated(state.copy(mode = AutoDJMode.PAUSED))
                }
            AutoDJUserCommand.RESUME ->
                if (state.mode != AutoDJMode.PAUSED) {
                    AutoDJGateDecision.Blocked(state, "Resume is available only after pausing Auto DJ.")
                } else {
                    AutoDJGateDecision.Updated(state.copy(mode = AutoDJMode.ACTIVE))
                }
        }
    }

    /**
     * Planning is permitted only while explicitly enabled and a current
     * local-library track is known. This does not authorize playback.
     */
    fun mayRequestNextPlan(state: AutoDJSessionState, currentTrackIsLocal: Boolean): Boolean =
        state.mode == AutoDJMode.ACTIVE &&
            state.currentTrackId != null &&
            currentTrackIsLocal

    /**
     * A proposed transition can be handed to a future playback coordinator
     * only if Auto DJ is still active, the user has not paused/stopped it,
     * and the plan was accepted by DJPlanValidator.
     */
    fun mayExecuteAcceptedPlan(
        state: AutoDJSessionState,
        currentTrackIsLocal: Boolean,
        planAccepted: Boolean
    ): Boolean =
        mayRequestNextPlan(state, currentTrackIsLocal) && planAccepted
}
