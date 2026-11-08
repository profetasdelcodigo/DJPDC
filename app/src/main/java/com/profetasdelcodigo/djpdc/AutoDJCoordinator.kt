package com.profetasdelcodigo.djpdc

/**
 * Pure decision coordinator between the local library, selector, planner and
 * session safety policy. It never touches a player or starts audio.
 */
sealed class AutoDJCoordinatorDecision {
    data class Ready(
        val nextTrack: LocalTrackEntry,
        val plan: DJTransitionPlan,
        val explanation: String
    ) : AutoDJCoordinatorDecision()

    data class Blocked(val reason: String) : AutoDJCoordinatorDecision()
}

/**
 * A short-lived approval token. Callers must re-check it immediately before
 * handing anything to a playback executor; it is not a playback command.
 */
data class AutoDJTransitionApproval(
    val currentTrackId: Long,
    val nextTrackId: Long,
    val plan: DJTransitionPlan
)

object AutoDJCoordinator {
    fun attachCurrentTrack(
        state: AutoDJSessionState,
        trackId: Long,
        availableLocalTrackIds: Set<Long>
    ): AutoDJSessionState? {
        if (trackId !in availableLocalTrackIds) return null
        return state.copy(currentTrackId = trackId)
    }

    fun proposeNext(
        state: AutoDJSessionState,
        library: List<LocalTrackEntry>,
        request: LocalTrackSelectionRequest = LocalTrackSelectionRequest(),
        transitionDurationMs: Long = 6_000L
    ): AutoDJCoordinatorDecision {
        val currentId = state.currentTrackId
            ?: return AutoDJCoordinatorDecision.Blocked("No current local track is selected.")
        val localIds = library.map { it.id }.toSet()
        if (!AutoDJSessionPolicy.mayRequestNextPlan(state, currentId in localIds)) {
            return AutoDJCoordinatorDecision.Blocked(
                "Auto DJ must be active and the current track must still be in the local library."
            )
        }

        return when (val decision = LocalDJPlanner.proposeNext(
            library = library,
            currentTrackId = currentId,
            request = request,
            autoDjEnabled = true,
            transitionDurationMs = transitionDurationMs
        )) {
            is LocalDJDecision.Ready -> AutoDJCoordinatorDecision.Ready(
                nextTrack = decision.nextTrack,
                plan = decision.transition,
                explanation = decision.explanation
            )
            is LocalDJDecision.Disabled -> AutoDJCoordinatorDecision.Blocked(decision.explanation)
            is LocalDJDecision.NoCandidate -> AutoDJCoordinatorDecision.Blocked(decision.explanation)
        }
    }

    /**
     * Re-check at handoff time, not only when the plan was created. This makes
     * a pause/stop or a library change invalidate an old proposal.
     */
    fun approveHandoff(
        state: AutoDJSessionState,
        approval: AutoDJTransitionApproval,
        availableLocalTrackIds: Set<Long>,
        nextTrackDurationMs: Long
    ): Boolean {
        if (state.currentTrackId != approval.currentTrackId) return false
        if (!AutoDJSessionPolicy.mayExecuteAcceptedPlan(
                state = state,
                currentTrackIsLocal = approval.currentTrackId in availableLocalTrackIds,
                planAccepted = true
            )
        ) return false
        if (approval.nextTrackId !in availableLocalTrackIds) return false
        if (approval.plan.nextTrackId != approval.nextTrackId) return false

        val validated = DJPlanValidator.validate(
            plan = approval.plan,
            context = DJPlanContext(
                autoDjEnabled = state.mode == AutoDJMode.ACTIVE,
                currentTrackId = state.currentTrackId,
                accessibleTrackIds = availableLocalTrackIds,
                nextTrackDurationMs = nextTrackDurationMs
            )
        )
        return validated is DJPlanDecision.Accepted &&
            validated.plan == approval.plan
    }
}
