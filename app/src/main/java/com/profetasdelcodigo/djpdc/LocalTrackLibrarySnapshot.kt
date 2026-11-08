package com.profetasdelcodigo.djpdc

/**
 * Immutable bridge between the Android MediaStore results and the pure local
 * selection/planning layer. The original Track objects (including content URIs)
 * are retained so callers can hand the selected item to the playback engine.
 */
data class LocalTrackLibrarySnapshot private constructor(
    val entries: List<LocalTrackEntry>,
    private val tracksById: Map<Long, Track>
) {
    fun trackFor(entry: LocalTrackEntry): Track? =
        tracksById[entry.id]?.takeIf {
            it.title == entry.title &&
                it.artist == entry.artist &&
                it.album == entry.album &&
                it.durationMs == entry.durationMs
        }

    fun trackForId(trackId: Long): Track? = tracksById[trackId]

    fun contains(trackId: Long): Boolean = tracksById.containsKey(trackId)

    companion object {
        fun fromTracks(tracks: List<Track>): LocalTrackLibrarySnapshot {
            val uniqueTracks = tracks.distinctBy { it.id }
            val entries = uniqueTracks.map { track ->
                LocalTrackEntry(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs
                )
            }
            return LocalTrackLibrarySnapshot(
                entries = entries,
                tracksById = uniqueTracks.associateBy { it.id }
            )
        }
    }
}
