package com.android.multimediastudio

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.multimediastudio.data.datasource.MediaStoreDataSourceImpl
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.model.ThemeMode
import com.android.multimediastudio.data.repository.MediaRepositoryImpl
import com.android.multimediastudio.data.repository.UserPreferencesRepository
import com.android.multimediastudio.ui.media.MediaDiscoveryScreen
import com.android.multimediastudio.util.VideoThumbnailFetcher
import com.android.multimediastudio.util.VideoThumbnailKeyer
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.android.multimediastudio.ui.media.MediaViewModel
import com.android.multimediastudio.ui.player.PlayerViewModel
import com.android.multimediastudio.ui.theme.MultimediaStudioTheme
import com.android.multimediastudio.util.ImmersiveUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mediaViewModel: MediaViewModel by viewModels {
        val dataSource = MediaStoreDataSourceImpl(applicationContext)
        val repository = MediaRepositoryImpl(dataSource)
        val prefsRepository = UserPreferencesRepository(applicationContext)
        MediaViewModel.provideFactory(repository, prefsRepository)
    }

    private val playerViewModel: PlayerViewModel by viewModels()

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_CONTROL) {
                val controlType = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)
                if (controlType == CONTROL_TYPE_PLAY) {
                    playerViewModel.play()
                } else if (controlType == CONTROL_TYPE_PAUSE) {
                    playerViewModel.pause()
                }
                val isPlaying = playerViewModel.uiState.value.isPlaying
                val isVideo = playerViewModel.uiState.value.currentMediaItem?.mediaType == MediaType.VIDEO
                updatePipParams(isPlaying, isPlaying && isVideo)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }

        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .components {
                add(VideoThumbnailFetcher.Factory(applicationContext))
                add(VideoThumbnailKeyer())
            }
            .crossfade(false)
            .build()
        Coil.setImageLoader(imageLoader)

        val filter = IntentFilter(ACTION_PIP_CONTROL)
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        playerViewModel.initializePlayer(applicationContext)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.uiState.collect { state ->
                    val isVideo = state.currentMediaItem?.mediaType == MediaType.VIDEO
                    val isVideoPlaying = state.isPlaying && isVideo

                    if (isVideoPlaying) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    updatePipParams(state.isPlaying, isVideoPlaying)
                }
            }
        }

        setContent {
            val mediaUiState by mediaViewModel.uiState.collectAsState()
            val darkTheme = when (mediaUiState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MultimediaStudioTheme(
                darkTheme = darkTheme,
                appThemeColor = mediaUiState.appThemeColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediaDiscoveryScreen(
                        viewModel = mediaViewModel,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && playerViewModel.uiState.value.isLandscape) {
            ImmersiveUtils.setImmersiveFullscreen(this, enabled = true)
        }
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) {
            // Receiver might not be registered
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val state = playerViewModel.uiState.value
            val isVideoPlaying = state.isPlaying && state.currentMediaItem?.mediaType == MediaType.VIDEO
            if (isVideoPlaying && !isInPictureInPictureMode) {
                playerViewModel.play()
                val builder = PictureInPictureParams.Builder()
                    .setActions(getPipActions(true))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                }
                try {
                    enterPictureInPictureMode(builder.build())
                } catch (_: Exception) {
                    // Ignore if PiP fails
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasPlaying = playerViewModel.uiState.value.isPlaying
        val currentItem = playerViewModel.uiState.value.currentMediaItem
        val isVideo = currentItem?.mediaType == MediaType.VIDEO

        playerViewModel.setInPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            if (wasPlaying || isVideo) {
                playerViewModel.play()
            }
        } else {
            if (isVideo) {
                playerViewModel.expandPlayer()
                mediaViewModel.selectMediaItem(currentItem)
            }
        }
    }

    private fun updatePipParams(isPlaying: Boolean, isVideoPlaying: Boolean = isPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setActions(getPipActions(isPlaying))

            try {
                val rational = Rational(16, 9)
                builder.setAspectRatio(rational)
            } catch (_: Exception) {
                // Ignore platform exceptions
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(isVideoPlaying)
            }

            try {
                setPictureInPictureParams(builder.build())
            } catch (_: Exception) {
                // Ignore platform exceptions
            }
        }
    }

    private fun getPipActions(isPlaying: Boolean): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        val actionIntent = Intent(ACTION_PIP_CONTROL).apply {
            setPackage(packageName)
            putExtra(EXTRA_CONTROL_TYPE, if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            if (isPlaying) REQUEST_CODE_PAUSE else REQUEST_CODE_PLAY,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play
        val title = if (isPlaying) "Pause" else "Play"

        val action = RemoteAction(
            Icon.createWithResource(this, iconRes),
            title,
            title,
            pendingIntent
        )

        return listOf(action)
    }

    companion object {
        private const val ACTION_PIP_CONTROL = "com.android.multimediastudio.PIP_CONTROL"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_TYPE_PLAY = 1
        private const val CONTROL_TYPE_PAUSE = 2
        private const val REQUEST_CODE_PLAY = 101
        private const val REQUEST_CODE_PAUSE = 102
    }
}
