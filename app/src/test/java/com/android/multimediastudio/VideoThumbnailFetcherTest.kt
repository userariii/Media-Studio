package com.android.multimediastudio

import com.android.multimediastudio.util.VideoThumbnailCache
import org.junit.Assert.assertNull
import org.junit.Test

class VideoThumbnailFetcherTest {

    @Test
    fun videoThumbnailCache_returnsNullForUncachedKey() {
        val cacheKey = "content://media/external/video/media/nonexistent_999"
        assertNull(VideoThumbnailCache.get(cacheKey))
    }
}
