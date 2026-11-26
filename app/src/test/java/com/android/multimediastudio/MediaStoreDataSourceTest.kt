package com.android.multimediastudio

import com.android.multimediastudio.data.datasource.MediaChangeEvent
import com.android.multimediastudio.data.datasource.extractMediaTitle
import com.android.multimediastudio.data.datasource.mergeMediaChangeEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreDataSourceTest {

    @Test
    fun extractMediaTitle_usesDisplayNameWithoutExtension_asPrimaryTitle() {
        val result = extractMediaTitle(
            displayName = "NewFilename.mp4",
            rawTitle = "Old ID3 Title",
            fallback = "Unknown Video"
        )
        assertEquals("NewFilename", result)
    }

    @Test
    fun extractMediaTitle_handlesAudioFilesWithExtension() {
        val result = extractMediaTitle(
            displayName = "SongTrack_v2.mp3",
            rawTitle = "Original ID3 Track",
            fallback = "Unknown Audio"
        )
        assertEquals("SongTrack_v2", result)
    }

    @Test
    fun extractMediaTitle_handlesDisplayNameWithoutExtension() {
        val result = extractMediaTitle(
            displayName = "MyVideoFile",
            rawTitle = "Original ID3 Title",
            fallback = "Unknown Video"
        )
        assertEquals("MyVideoFile", result)
    }

    @Test
    fun extractMediaTitle_fallsBackToRawTitle_whenDisplayNameIsBlankOrNull() {
        val resultBlank = extractMediaTitle(
            displayName = "   ",
            rawTitle = "Original ID3 Title",
            fallback = "Unknown Video"
        )
        assertEquals("Original ID3 Title", resultBlank)

        val resultNull = extractMediaTitle(
            displayName = null,
            rawTitle = "Original ID3 Title",
            fallback = "Unknown Video"
        )
        assertEquals("Original ID3 Title", resultNull)
    }

    @Test
    fun extractMediaTitle_fallsBackToFallback_whenBothDisplayNameAndRawTitleAreBlankOrNull() {
        val result = extractMediaTitle(
            displayName = null,
            rawTitle = null,
            fallback = "Unknown Video"
        )
        assertEquals("Unknown Video", result)
    }

    @Test
    fun extractMediaTitle_fallsBackToRawTitle_whenExtensionRemovalResultsInBlank() {
        val result = extractMediaTitle(
            displayName = ".mp4",
            rawTitle = "Some Raw Title",
            fallback = "Unknown Video"
        )
        assertEquals("Some Raw Title", result)
    }

    @Test
    fun mergeMediaChangeEvents_combinesUriIdsAndPromotesCollectionFlags() {
        val a = MediaChangeEvent.UriChanges(videoIds = setOf(1L), audioIds = setOf(10L))
        val b = MediaChangeEvent.UriChanges(videoIds = setOf(2L), refreshAudioCollection = true)

        val merged = mergeMediaChangeEvents(a, b) as MediaChangeEvent.UriChanges
        assertEquals(setOf(1L, 2L), merged.videoIds)
        assertTrue(merged.audioIds.isEmpty())
        assertTrue(merged.refreshAudioCollection)
    }

    @Test
    fun mergeMediaChangeEvents_fullSyncNeededOverridesUriChanges() {
        val a = MediaChangeEvent.UriChanges(videoIds = setOf(1L))
        val merged = mergeMediaChangeEvents(a, MediaChangeEvent.FullSyncNeeded)
        assertEquals(MediaChangeEvent.FullSyncNeeded, merged)
    }
}
