package com.profetasdelcodigo.djpdc

/**
 * Conservative on-device decision layer.
 *
 * BPM-aware ranking is used only when measured tempos are available and the
 * required correction is within the safe range. Otherwise metadata ranking
 * remains the fallback. No beat alignment is claimed.
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
        transitionDurationMs: Long = 6_000L,
        currentBpm: Float? = null,
        measuredBpms: Map<Long, Float> = emptyMap(),
        currentEnergyProfile: TrackEnergyProfile? = null,
        measuredEnergyProfiles: Map<Long, TrackEnergyProfile> = emptyMap()
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
        // Auto DJ asks for one final result, but BPM matching needs a wider
        // candidate pool so it can compare several already-ranked local tracks.
        val ranked = LocalTrackSelector.rank(
            library,
            scopedRequest.copy(limit = maxOf(request.limit, minOf(library.size, 20)))
        )
        val bpmSelection = BpmAwareTrackSelector.select(
            ranked = ranked,
            currentBpm = currentBpm,
            measuredBpms = measuredBpms
        )
        val energyCandidates = if (currentBpm != null && measuredBpms.isNotEmpty()) {
            ranked.filter { candidate ->
                val bpm = measuredBpms[candidate.track.id]
                bpm != null && BeatmatchMath.matchingSpeed(currentBpm, bpm) != null
            }.ifEmpty { ranked }
        } else ranked
        val energySelection = EnergyAwareTrackSelector.select(
            ranked = energyCandidates,
            current = currentEnergyProfile,
            measured = measuredEnergyProfiles
        )
        val selected = if (currentEnergyProfile != null) energySelection else bpmSelection?.track ?: ranked.firstOrNull()
        val next = selected ?: return LocalDJDecision.NoCandidate(
            "No matching next track was found in the phone's available music library."
        )
        val selectedBpmRatio = measuredBpms[next.track.id]?.let { candidateBpm ->
            currentBpm?.let { BeatmatchMath.matchingSpeed(it, candidateBpm) }
        }
        val playbackRatio = if (bpmSelection?.track?.track?.id == next.track.id) {
            bpmSelection.playbackSpeedRatio
        } else selectedBpmRatio ?: 1f

        val plan = DJTransitionPlan(
            nextTrackId = next.track.id,
            transitionDurationMs = transitionDurationMs,
            nextTrackStartMs = 0L,
            playbackSpeedRatio = playbackRatio,
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
                explanation = buildString {
                    append("Selected from the phone's local library.")
                    if (selectedBpmRatio != null) append(" Tempo compatibility was measured.")
                    if (currentEnergyProfile != null && measuredEnergyProfiles[next.track.id] != null) {
                        append(" Energy and transient density were considered for a smoother party flow.")
                    }
                    append(" A simple fade is proposed; beat-grid alignment is not yet available.")
                }
            )
            is DJPlanDecision.Rejected -> LocalDJDecision.NoCandidate(
                "The local safety checks rejected this transition: ${decision.reason}."
            )
        }
    }
}
