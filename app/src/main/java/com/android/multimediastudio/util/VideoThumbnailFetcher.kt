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
import android.os.CancellationSignal
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
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.memory.MemoryCache
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "VideoThumbnailFetcher"
private const val THUMB_MIN_PX = 128
private const val THUMB_MAX_PX = 512

data class VideoThumbnailData(
    val uri: Uri,
    val durationMs: Long = 0L
)

data class AudioThumbnailData(
    val contentUri: Uri?,
    val albumArtUri: Uri?
)

object VideoThumbnailCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 16).coerceAtLeast(1024 * 4)

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

internal object ThumbnailNegativeCache {
    private val cache = object : LruCache<String, Boolean>(1024) {}

    fun isFailed(key: String): Boolean = cache.get(key) == true

    fun markFailed(key: String) {
        cache.put(key, true)
    }

    fun invalidate(uriKey: String) {
        cache.remove("video_thumb_$uriKey")
        cache.remove("audio_art_$uriKey")
    }
}

object AppImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { loader ->
                instance = loader
            }
        }
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .components {
                add(VideoThumbnailFetcher.Factory(appContext))
                add(VideoThumbnailKeyer())
                add(AudioThumbnailFetcher.Factory(appContext))
                add(AudioThumbnailKeyer())
            }
            .crossfade(false)
            .build()
    }
}

private val thumbnailDecoderDispatcher = Dispatchers.IO.limitedParallelism(3)

private fun resolveTargetSize(options: Options, defaultWidth: Int = 512, defaultHeight: Int = 288): Pair<Int, Int> {
    val reqW = options.size.width.pxOrElse { defaultWidth }.coerceIn(THUMB_MIN_PX, THUMB_MAX_PX)
    val reqH = options.size.height.pxOrElse { defaultHeight }.coerceIn(THUMB_MIN_PX, THUMB_MAX_PX)
    return Pair(reqW, reqH)
}

private fun decodeSampledByteArray(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while ((bounds.outWidth / (sampleSize * 2)) >= targetW &&
        (bounds.outHeight / (sampleSize * 2)) >= targetH
    ) {
        sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
}

private fun decodeSampledStream(
    context: Context,
    uri: Uri,
    targetW: Int,
    targetH: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while ((bounds.outWidth / (sampleSize * 2)) >= targetW &&
        (bounds.outHeight / (sampleSize * 2)) >= targetH
    ) {
        sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOpts)
    }
}

private fun scaleDownIfOversized(source: Bitmap, maxW: Int, maxH: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
        return source
    }
    val w = source.width
    val h = source.height
    if (w <= 0 || h <= 0 || (w <= maxW * 2 && h <= maxH * 2)) {
        return source
    }
    val ratio = minOf(maxW.toFloat() / w.toFloat(), maxH.toFloat() / h.toFloat())
    val dstW = (w * ratio).toInt().coerceAtLeast(1)
    val dstH = (h * ratio).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, dstW, dstH, true)
    if (scaled !== source) {
        source.recycle()
    }
    return scaled
}

@OptIn(ExperimentalCoilApi::class)
private fun readFromDiskCache(diskCache: DiskCache?, diskKey: String): SourceResult? {
    val snapshot = try {
        diskCache?.openSnapshot(diskKey)
    } catch (_: Exception) {
        null
    } ?: return null

    return try {
        SourceResult(
            source = ImageSource(
                file = snapshot.data,
                fileSystem = diskCache!!.fileSystem,
                diskCacheKey = diskKey,
                closeable = snapshot
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    } catch (_: Exception) {
        try { snapshot.close() } catch (_: Exception) {}
        null
    }
}

@OptIn(ExperimentalCoilApi::class)
private fun writeToDiskCache(diskCache: DiskCache?, diskKey: String, bitmap: Bitmap) {
    if (diskCache == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        return
    }
    var editor: DiskCache.Editor? = null
    try {
        editor = diskCache.openEditor(diskKey) ?: return
        val out = ByteArrayOutputStream(16 * 1024)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val bytes = out.toByteArray()
        diskCache.fileSystem.write(editor.data) {
            write(bytes)
        }
        editor.commit()
    } catch (_: Exception) {
        try { editor?.abort() } catch (_: Exception) {}
    }
}

private fun isBitmapBlankOrBlack(bitmap: Bitmap): Boolean {
    if (bitmap.width <= 0 || bitmap.height <= 0) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        return false
    }
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

class VideoThumbnailFetcher(
    private val context: Context,
    private val data: VideoThumbnailData,
    private val options: Options,
    private val diskCache: DiskCache?
) : Fetcher {

    @Suppress("DEPRECATION")
    override suspend fun fetch(): FetchResult? {
        val uriKey = data.uri.toString()
        val diskKey = "video_thumb_$uriKey"

        if (ThumbnailNegativeCache.isFailed(diskKey)) {
            return null
        }

        readFromDiskCache(diskCache, diskKey)?.let { cached ->
            return cached
        }

        val (targetW, targetH) = resolveTargetSize(options, defaultWidth = 512, defaultHeight = 288)

        return withContext(thumbnailDecoderDispatcher) {
            currentCoroutineContext().ensureActive()

            readFromDiskCache(diskCache, diskKey)?.let { cached ->
                return@withContext cached
            }

            var bitmap: Bitmap? = null

            // 1. ContentResolver.loadThumbnail (Android 10+ system thumbnail service)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val signal = CancellationSignal()
                val jobHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                    if (cause != null) signal.cancel()
                }
                try {
                    val loadedBitmap = context.contentResolver.loadThumbnail(
                        data.uri,
                        Size(targetW, targetH),
                        signal
                    )
                    if (!isBitmapBlankOrBlack(loadedBitmap)) {
                        bitmap = loadedBitmap
                    } else {
                        loadedBitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "ContentResolver.loadThumbnail unavailable for $uriKey: ${e.message}")
                } finally {
                    jobHandle.dispose()
                }
            }

            currentCoroutineContext().ensureActive()

            // 2. MediaStore.Video.Thumbnails (Legacy Android MediaStore)
            if (bitmap == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
                            bitmap = scaleDownIfOversized(msBitmap, targetW, targetH)
                        } else {
                            msBitmap?.recycle()
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "MediaStore.Video.Thumbnails unavailable for $uriKey: ${e.message}")
                }
            }

            currentCoroutineContext().ensureActive()

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

                    val embeddedPicture = retriever.embeddedPicture
                    if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                        val decoded = decodeSampledByteArray(embeddedPicture, targetW, targetH)
                        if (decoded != null && !isBitmapBlankOrBlack(decoded)) {
                            bitmap = decoded
                        } else {
                            decoded?.recycle()
                        }
                    }

                    currentCoroutineContext().ensureActive()

                    if (bitmap == null) {
                        var durationMs = data.durationMs
                        if (durationMs <= 0L) {
                            val durationMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            durationMs = durationMeta?.toLongOrNull() ?: 0L
                        }

                        val targetTimeUs = if (durationMs > 0L) (durationMs * 1000L) / 10 else 1_000_000L

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            try {
                                val scaled = retriever.getScaledFrameAtTime(
                                    targetTimeUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                    targetW.coerceAtLeast(320),
                                    targetH.coerceAtLeast(180)
                                )
                                if (scaled != null && !isBitmapBlankOrBlack(scaled)) {
                                    bitmap = scaled
                                } else {
                                    scaled?.recycle()
                                }
                            } catch (_: Exception) {}
                        }

                        if (bitmap == null) {
                            currentCoroutineContext().ensureActive()
                            try {
                                val candidate = retriever.getFrameAtTime(
                                    targetTimeUs,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                )
                                if (candidate != null && !isBitmapBlankOrBlack(candidate)) {
                                    bitmap = scaleDownIfOversized(candidate, targetW, targetH)
                                } else {
                                    candidate?.recycle()
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

            currentCoroutineContext().ensureActive()

            // 4. MediaCodec + Surface + PixelCopy Fallback (for 10-bit HEVC/x265 or MKV files)
            if (bitmap == null || isBitmapBlankOrBlack(bitmap)) {
                Log.d(TAG, "Falling back to MediaCodec Surface + PixelCopy for ${data.uri}")
                val codecBitmap = extractFrameWithCodecAndPixelCopy(context, data.uri, data.durationMs, targetW, targetH)
                if (codecBitmap != null && !isBitmapBlankOrBlack(codecBitmap)) {
                    bitmap = codecBitmap
                } else {
                    codecBitmap?.recycle()
                }
            }

            val finalBitmap = bitmap
            if (finalBitmap != null && !isBitmapBlankOrBlack(finalBitmap)) {
                writeToDiskCache(diskCache, diskKey, finalBitmap)
                DrawableResult(
                    drawable = finalBitmap.toDrawable(context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } else {
                finalBitmap?.recycle()
                ThumbnailNegativeCache.markFailed(diskKey)
                null
            }
        }
    }

    private suspend fun extractFrameWithCodecAndPixelCopy(
        context: Context,
        uri: Uri,
        durationMs: Long,
        maxTargetW: Int,
        maxTargetH: Int
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
            if (width <= 0 || height <= 0) return@withContext null
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
            var targetW = maxTargetW.coerceAtLeast(256)
            var targetH = (targetW / aspect).toInt().coerceAtLeast(1)
            val boundH = maxTargetH.coerceAtLeast(180)
            if (targetH > boundH) {
                targetH = boundH
                targetW = (targetH * aspect).toInt().coerceAtLeast(1)
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
            while (isActive && !frameRendered && (System.currentTimeMillis() - startTimeMs) < 2000L) {
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

            if (isActive && frameRendered) {
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
                } else {
                    bitmap.recycle()
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

    class Factory(context: Context) : FactoryFetcher(context)
}

open class FactoryFetcher(private val context: Context) : Fetcher.Factory<VideoThumbnailData> {
    override fun create(data: VideoThumbnailData, options: Options, imageLoader: ImageLoader): Fetcher {
        return VideoThumbnailFetcher(context, data, options, imageLoader.diskCache)
    }
}

class VideoThumbnailKeyer : coil.key.Keyer<VideoThumbnailData> {
    override fun key(data: VideoThumbnailData, options: Options): String {
        return "video_${data.uri}"
    }
}

class AudioThumbnailFetcher(
    private val context: Context,
    private val data: AudioThumbnailData,
    private val options: Options,
    private val diskCache: DiskCache?
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val primaryUri = data.contentUri ?: data.albumArtUri ?: return null
        val diskKey = "audio_art_$primaryUri"

        if (ThumbnailNegativeCache.isFailed(diskKey)) {
            return null
        }

        readFromDiskCache(diskCache, diskKey)?.let { cached ->
            return cached
        }

        val (targetW, targetH) = resolveTargetSize(options, defaultWidth = 384, defaultHeight = 384)

        return withContext(thumbnailDecoderDispatcher) {
            currentCoroutineContext().ensureActive()

            readFromDiskCache(diskCache, diskKey)?.let { cached ->
                return@withContext cached
            }

            var bitmap: Bitmap? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && data.contentUri != null) {
                val signal = CancellationSignal()
                val jobHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                    if (cause != null) signal.cancel()
                }
                try {
                    bitmap = context.contentResolver.loadThumbnail(
                        data.contentUri,
                        Size(targetW, targetH),
                        signal
                    )
                } catch (_: Exception) {
                } finally {
                    jobHandle.dispose()
                }
            }

            currentCoroutineContext().ensureActive()

            if (bitmap == null && data.albumArtUri != null) {
                try {
                    bitmap = decodeSampledStream(context, data.albumArtUri, targetW, targetH)
                } catch (_: Exception) {
                }
            }

            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                writeToDiskCache(diskCache, diskKey, bitmap)
                DrawableResult(
                    drawable = bitmap.toDrawable(context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } else {
                bitmap?.recycle()
                ThumbnailNegativeCache.markFailed(diskKey)
                null
            }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioThumbnailData> {
        override fun create(data: AudioThumbnailData, options: Options, imageLoader: ImageLoader): Fetcher {
            return AudioThumbnailFetcher(context, data, options, imageLoader.diskCache)
        }
    }
}

class AudioThumbnailKeyer : coil.key.Keyer<AudioThumbnailData> {
    override fun key(data: AudioThumbnailData, options: Options): String {
        val primaryUri = data.contentUri ?: data.albumArtUri ?: Uri.EMPTY
        return "audio_$primaryUri"
    }
}
