package com.android.multimediastudio

import com.android.multimediastudio.util.ThumbnailNegativeCache
import com.android.multimediastudio.util.VideoThumbnailCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailFetcherTest {

    @Test
    fun videoThumbnailCache_returnsNullForUncachedKey() {
        val cacheKey = "content://media/external/video/media/nonexistent_999"
        assertNull(VideoThumbnailCache.get(cacheKey))
    }

    @Test
    fun thumbnailNegativeCache_tracksFailedKeys() {
        val key = "test_audio:content://media/external/audio/media/99999"
        assertFalse(ThumbnailNegativeCache.isFailed(key))
        ThumbnailNegativeCache.markFailed(key)
        assertTrue(ThumbnailNegativeCache.isFailed(key))
    }
}
