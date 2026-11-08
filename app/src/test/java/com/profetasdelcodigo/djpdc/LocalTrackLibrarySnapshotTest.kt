package com.profetasdelcodigo.djpdc

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTrackLibrarySnapshotTest {
    private fun track(
        id: Long,
        title: String = "Track $id",
        uri: String = "content://media/external/audio/media/$id"
    ) = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        contentUri = Uri.parse(uri),
        albumArtUri = null
    )

    @Test
    fun mapsMediaStoreTracksToPlannerEntriesAndPreservesOriginalTrack() {
        val original = track(7L)
        val snapshot = LocalTrackLibrarySnapshot.fromTracks(listOf(original))

        assertEquals(listOf(LocalTrackEntry(7L, "Track 7", "Artist", "Album", 180_000L)), snapshot.entries)
        assertSame(original, snapshot.trackForId(7L))
        assertEquals(original.contentUri, snapshot.trackFor(snapshot.entries.single())?.contentUri)
    }

    @Test
    fun removesDuplicateIdsWithoutChangingFirstTrack() {
        val first = track(7L, "First")
        val duplicate = track(7L, "Duplicate", "content://different")
        val snapshot = LocalTrackLibrarySnapshot.fromTracks(listOf(first, duplicate))

        assertEquals(1, snapshot.entries.size)
        assertEquals("First", snapshot.entries.single().title)
        assertSame(first, snapshot.trackForId(7L))
    }

    @Test
    fun unknownIdsDoNotResolveToTracks() {
        val snapshot = LocalTrackLibrarySnapshot.fromTracks(listOf(track(1L)))

        assertNull(snapshot.trackForId(99L))
        assertTrue(!snapshot.contains(99L))
        assertNotNull(snapshot.trackForId(1L))
    }

    @Test
    fun mismatchedEntryMetadataCannotResolveToOriginalTrack() {
        val snapshot = LocalTrackLibrarySnapshot.fromTracks(listOf(track(1L)))

        assertNull(snapshot.trackFor(LocalTrackEntry(1L, "Other title", "Artist", "Album", 180_000L)))
    }
}
