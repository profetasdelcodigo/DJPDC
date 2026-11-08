package com.profetasdelcodigo.djpdc

/**
 * Conservative on-device decision layer.
 *
 * It can choose a next item only from the supplied local-library snapshot.
 * Until reliable beat grids exist, it proposes a simple fade from the start
 * of the next track and never claims beat alignment.
 */
sealed class LocalDJDecision {
    data class Ready(
        val nextTrack: LocalTrackEntry,
        val transition: DJTransitionPlan,
        val explanation: String
    ) : LocalDJDecision()

    data class NoCandidate(val explanation: String) : LocalDJDecision()
    data class Disabled(val explanation: String = "Auto DJ is disabled") : LocalDJDecision()
}

object LocalDJPlanner {
    fun proposeNext(
        library: List<LocalTrackEntry>,
        currentTrackId: Long?,
        request: LocalTrackSelectionRequest,
        autoDjEnabled: Boolean,
        transitionDurationMs: Long = 6_000L
    ): LocalDJDecision {
        if (!autoDjEnabled) return LocalDJDecision.Disabled()
        if (currentTrackId == null) {
            return LocalDJDecision.NoCandidate("Start a local track before requesting an automatic transition.")
        }

        val availableIds = library.map { it.id }.toSet()
        if (currentTrackId !in availableIds) {
            return LocalDJDecision.NoCandidate("The current track is no longer in the available local library.")
        }

        if (request.limit <= 0) {
            return LocalDJDecision.NoCandidate("The selection limit must be greater than zero.")
        }

        val scopedRequest = request.copy(
            allowedTrackIds = request.allowedTrackIds?.intersect(availableIds) ?: availableIds,
            excludedTrackIds = request.excludedTrackIds + currentTrackId
        )
        val next = LocalTrackSelector.rank(library, scopedRequest).firstOrNull()
            ?: return LocalDJDecision.NoCandidate(
                "No matching next track was found in the phone's available music library."
            )

        val plan = DJTransitionPlan(
            nextTrackId = next.track.id,
            transitionDurationMs = transitionDurationMs,
            nextTrackStartMs = 0L,
            playbackSpeedRatio = 1f,
            beatAlignmentConfidence = 0f,
            style = DJTransitionStyle.SIMPLE_FADE
        )
        val decision = DJPlanValidator.validate(
            plan,
            DJPlanContext(
                autoDjEnabled = autoDjEnabled,
                currentTrackId = currentTrackId,
                accessibleTrackIds = availableIds,
                nextTrackDurationMs = next.track.durationMs
            )
        )
        return when (decision) {
            is DJPlanDecision.Accepted -> LocalDJDecision.Ready(
                nextTrack = next.track,
                transition = decision.plan,
                explanation = "Selected from the phone library using available title, artist and album metadata. A simple fade is proposed; beat matching is not yet available."
            )
            is DJPlanDecision.Rejected -> LocalDJDecision.NoCandidate(
                "The local safety checks rejected this transition: ${decision.reason}."
            )
        }
    }
}
