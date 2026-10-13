package com.android.multimediastudio.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.PixelCopy
import android.view.Surface
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "VideoThumbnailFetcher"

data class VideoThumbnailData(
    val uri: Uri,
    val durationMs: Long = 0L
)

object VideoThumbnailCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceAtLeast(1024 * 16)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

private val thumbnailDecoderDispatcher = Dispatchers.IO.limitedParallelism(3)

class VideoThumbnailFetcher(
    private val context: Context,
    private val data: VideoThumbnailData
) : Fetcher {

    @Suppress("DEPRECATION")
    override suspend fun fetch(): FetchResult? {
        val cacheKey = data.uri.toString()

        val cachedBitmap = VideoThumbnailCache.get(cacheKey)
        if (cachedBitmap != null) {
            return DrawableResult(
                drawable = cachedBitmap.toDrawable(context.resources),
                isSampled = false,
                dataSource = DataSource.MEMORY
            )
        }

        return withContext(thumbnailDecoderDispatcher) {
            val secondCheck = VideoThumbnailCache.get(cacheKey)
            if (secondCheck != null) {
                return@withContext DrawableResult(
                    drawable = secondCheck.toDrawable(context.resources),
                    isSampled = false,
                    dataSource = DataSource.MEMORY
                )
            }

            var bitmap: Bitmap? = null

            // 1. ContentResolver.loadThumbnail (Android 10+ system thumbnail service)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val loadedBitmap = context.contentResolver.loadThumbnail(data.uri, Size(512, 288), null)
                    if (!isBitmapBlankOrBlack(loadedBitmap)) {
                        bitmap = loadedBitmap
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "ContentResolver.loadThumbnail unavailable for $cacheKey: ${e.message}")
                }
            }

        // 2. MediaStore.Video.Thumbnails (Legacy Android MediaStore)
        if (bitmap == null) {
            try {
                val id = try { ContentUris.parseId(data.uri) } catch (_: Exception) { -1L }
                if (id != -1L) {
                    val msBitmap = MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                    if (msBitmap != null && !isBitmapBlankOrBlack(msBitmap)) {
                        bitmap = msBitmap
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "MediaStore.Video.Thumbnails unavailable for $cacheKey: ${e.message}")
            }
        }

        // 3. MediaMetadataRetriever (Native Stagefright frame extraction around 10% duration)
        if (bitmap == null) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, data.uri)
                } catch (_: Exception) {
                    context.contentResolver.openFileDescriptor(data.uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                    }
                }

                // Check for embedded picture / album art
                val embeddedPicture = retriever.embeddedPicture
                if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                    val decoded = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                    if (decoded != null && !isBitmapBlankOrBlack(decoded)) {
                        bitmap = decoded
                    }
                }

                if (bitmap == null) {
                    var durationMs = data.durationMs
                    if (durationMs <= 0L) {
                        val durationMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        durationMs = durationMeta?.toLongOrNull() ?: 0L
                    }

                    val targetTimeUs = if (durationMs > 0L) (durationMs * 1000L) / 10 else 1_000_000L

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        try {
                            val scaled = retriever.getScaledFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
                            if (scaled != null && !isBitmapBlankOrBlack(scaled)) {
                                bitmap = scaled
                            }
                        } catch (_: Exception) {}
                    }

                    if (bitmap == null) {
                        try {
                            val candidate = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (candidate != null && !isBitmapBlankOrBlack(candidate)) {
                                bitmap = candidate
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaMetadataRetriever failed for ${data.uri}: ${e.message}")
            } finally {
                try { retriever?.release() } catch (_: Exception) {}
            }
        }

        // 4. MediaCodec + Surface + PixelCopy Fallback (for 10-bit HEVC/x265 or MKV files where native retriever returns null)
        if (bitmap == null || isBitmapBlankOrBlack(bitmap)) {
            Log.d(TAG, "Falling back to MediaCodec Surface + PixelCopy for ${data.uri}")
            val codecBitmap = extractFrameWithCodecAndPixelCopy(context, data.uri, data.durationMs)
            if (codecBitmap != null && !isBitmapBlankOrBlack(codecBitmap)) {
                bitmap = codecBitmap
            }
        }

        if (bitmap != null && !isBitmapBlankOrBlack(bitmap)) {
            VideoThumbnailCache.put(cacheKey, bitmap)
            DrawableResult(
                drawable = bitmap.toDrawable(context.resources),
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } else {
            null
        }
    }
}

    private suspend fun extractFrameWithCodecAndPixelCopy(
        context: Context,
        uri: Uri,
        durationMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var surfaceTexture: SurfaceTexture? = null
        var surface: Surface? = null
        var handlerThread: HandlerThread? = null

        try {
            extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
            } catch (_: Exception) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                }
            }

            var videoTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (videoTrackIndex < 0 || format == null) return@withContext null

            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null

            var durMs = durationMs
            if (durMs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                durMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
            }

            val targetTimeUs = if (durMs > 0L) (durMs * 1000L) / 10 else 1_000_000L
            extractor.selectTrack(videoTrackIndex)
            extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            handlerThread = HandlerThread("PixelCopyThread").apply { start() }
            val handler = Handler(handlerThread.looper)

            val aspect = width.toFloat() / height.toFloat()
            var targetW = 640
            var targetH = (targetW / aspect).toInt()
            if (targetH > 360) {
                targetH = 360
                targetW = (targetH * aspect).toInt()
            }

            surfaceTexture = SurfaceTexture(10)
            surfaceTexture.setDefaultBufferSize(targetW, targetH)
            surface = Surface(surfaceTexture)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, surface, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var frameRendered = false

            val startTimeMs = System.currentTimeMillis()
            while (!frameRendered && (System.currentTimeMillis() - startTimeMs) < 2500L) {
                if (!sawInputEOS) {
                    val inputIndex = decoder.dequeueInputBuffer(10000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, 10000L)
                if (outputIndex >= 0) {
                    if (info.size > 0) {
                        decoder.releaseOutputBuffer(outputIndex, true)
                        frameRendered = true
                    } else {
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            if (frameRendered) {
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val latch = CountDownLatch(1)
                var copyResult = PixelCopy.ERROR_UNKNOWN

                PixelCopy.request(surface, bitmap, { result ->
                    copyResult = result
                    latch.countDown()
                }, handler)

                latch.await(1000, TimeUnit.MILLISECONDS)

                if (copyResult == PixelCopy.SUCCESS && !isBitmapBlankOrBlack(bitmap)) {
                    return@withContext bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PixelCopy extraction failed for $uri: ${e.message}")
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { surface?.release() } catch (_: Exception) {}
            try { surfaceTexture?.release() } catch (_: Exception) {}
            try { handlerThread?.quitSafely() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
        null
    }

    private fun isBitmapBlankOrBlack(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return true
        val w = bitmap.width
        val h = bitmap.height
        var nonBlackPixels = 0
        for (i in 1..5) {
            for (j in 1..5) {
                val x = (w * i) / 6
                val y = (h * j) / 6
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val alpha = (pixel shr 24) and 0xFF
                if (alpha > 0 && (r > 15 || g > 15 || b > 15)) {
                    nonBlackPixels++
                }
            }
        }
        return nonBlackPixels == 0
    }

    class Factory(context: Context) : FactoryFetcher(context)
}

open class FactoryFetcher(private val context: Context) : Fetcher.Factory<VideoThumbnailData> {
    override fun create(data: VideoThumbnailData, options: Options, imageLoader: ImageLoader): Fetcher {
        return VideoThumbnailFetcher(context, data)
    }
}

class VideoThumbnailKeyer : coil.key.Keyer<VideoThumbnailData> {
    override fun key(data: VideoThumbnailData, options: Options): String {
        return data.uri.toString()
    }
}
