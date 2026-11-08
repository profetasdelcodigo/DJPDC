package com.profetasdelcodigo.djpdc

/**
 * Ranks only metadata already present in the user's permitted local Android music library.
 * It deliberately does not infer genre, mood, tempo, or musical compatibility.
 */
data class LocalTrackEntry(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
)

data class LocalTrackSelectionRequest(
    val query: String = "",
    val allowedTrackIds: Set<Long>? = null,
    val favoriteTrackIds: Set<Long> = emptySet(),
    val excludedTrackIds: Set<Long> = emptySet(),
    val limit: Int = 20
)

data class RankedLocalTrack(
    val track: LocalTrackEntry,
    val score: Int,
    val matchedFields: Set<String>
)

object LocalTrackSelector {
    fun rank(
        library: List<LocalTrackEntry>,
        request: LocalTrackSelectionRequest
    ): List<RankedLocalTrack> {
        if (request.limit <= 0) return emptyList()

        val queryTokens = tokenize(request.query)
        return library
            .asSequence()
            .distinctBy { it.id }
            .filter { it.id !in request.excludedTrackIds }
            .filter { request.allowedTrackIds == null || it.id in request.allowedTrackIds }
            .mapNotNull { track ->
                val fields = linkedMapOf(
                    "title" to track.title,
                    "artist" to track.artist,
                    "album" to track.album
                )
                val matched = fields.filterValues { value ->
                    queryTokens.isNotEmpty() && queryTokens.any { token ->
                        normalize(value).contains(token)
                    }
                }.keys
                if (queryTokens.isNotEmpty() && matched.isEmpty()) {
                    null
                } else {
                    var score = matched.sumOf { field ->
                        when (field) {
                            "title" -> 5
                            "artist" -> 4
                            else -> 2
                        }
                    }
                    if (request.favoriteTrackIds.contains(track.id)) score += 3
                    RankedLocalTrack(track, score, matched)
                }
            }
            .sortedWith(
                compareByDescending<RankedLocalTrack> { it.score }
                    .thenBy { it.track.title.lowercase() }
                    .thenBy { it.track.id }
            )
            .take(request.limit)
            .toList()
    }

    private fun tokenize(value: String): List<String> =
        normalize(value).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }
            .distinct()

    private fun normalize(value: String): String =
        value.trim().lowercase()
}
