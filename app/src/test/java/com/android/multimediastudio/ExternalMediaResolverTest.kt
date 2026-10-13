package com.android.multimediastudio

import android.content.ContextWrapper
import android.content.Intent
import com.android.multimediastudio.util.ExternalMediaResolver
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMediaResolverTest {

    @Test
    fun resolveMediaItem_returnsNullWhenIntentHasNoUri() {
        val context = ContextWrapper(null)
        val intent = Intent(Intent.ACTION_VIEW)

        val result = ExternalMediaResolver.resolveMediaItem(context, intent)
        assertNull(result)
    }
}
