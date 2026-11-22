package com.android.multimediastudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    interface QueueNavigator {
        fun onSkipNext()
        fun onSkipPrevious()
        fun canSkipNext(): Boolean
        fun canSkipPrevious(): Boolean
    }

    class QueueForwardingPlayer(
        player: Player,
        private val mainHandler: Handler,
    ) : ForwardingPlayer(player) {

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                    queueNavigator?.canSkipNext() ?: true
                COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                    queueNavigator?.canSkipPrevious() ?: true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun hasNextMediaItem(): Boolean {
            return queueNavigator?.canSkipNext() ?: true
        }

        override fun hasPreviousMediaItem(): Boolean {
            return queueNavigator?.canSkipPrevious() ?: true
        }

        override fun seekToNext() {
            mainHandler.post {
                queueNavigator?.onSkipNext()
            }
        }

        override fun seekToNextMediaItem() {
            mainHandler.post {
                queueNavigator?.onSkipNext()
            }
        }

        override fun seekToPrevious() {
            mainHandler.post {
                if (currentPosition > 3000L) {
                    seekTo(0L)
                } else {
                    queueNavigator?.onSkipPrevious()
                }
            }
        }

        override fun seekToPreviousMediaItem() {
            mainHandler.post {
                if (currentPosition > 3000L) {
                    seekTo(0L)
                } else {
                    queueNavigator?.onSkipPrevious()
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIFICATION_ID = 1001

        var queueNavigator: QueueNavigator? = null

        private var sharedPlayer: ExoPlayer? = null
        private var sharedForwardingPlayer: QueueForwardingPlayer? = null
        private var sharedMediaSession: MediaSession? = null

        fun getSharedPlayer(context: Context): ExoPlayer {
            val player = sharedPlayer
            if ((player != null) && isPlayerThreadAlive(player)) return player

            return synchronized(this) {
                val existing = sharedPlayer
                if (existing != null && isPlayerThreadAlive(existing)) {
                    existing
                } else {
                    try {
                        existing?.release()
                    } catch (_: Exception) {}

                    val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

                    val newPlayer = ExoPlayer.Builder(context.applicationContext)
                        .setRenderersFactory(renderersFactory)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .setUsage(C.USAGE_MEDIA)
                                .build(),
                            /* handleAudioFocus = */ true
                        )
                        .setHandleAudioBecomingNoisy(true)
                        .build()

                    sharedPlayer = newPlayer
                    newPlayer
                }
            }
        }

        private fun isPlayerThreadAlive(player: ExoPlayer): Boolean {
            return try {
                player.playbackLooper.thread.isAlive && player.applicationLooper.thread.isAlive
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.media_playback_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_pip_play)
        setMediaNotificationProvider(notificationProvider)

        val player = getSharedPlayer(this)
        val mainHandler = Handler(Looper.getMainLooper())
        val forwardingPlayer = QueueForwardingPlayer(player, mainHandler)
        sharedForwardingPlayer = forwardingPlayer

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val baseCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(baseCommands)
                    .build()
            }
        }

        if (sharedMediaSession == null) {
            sharedMediaSession = MediaSession.Builder(this, forwardingPlayer)
                .setSessionActivity(sessionActivityPendingIntent)
                .setCallback(sessionCallback)
                .build()
        }
        mediaSession = sharedMediaSession
        mediaSession?.let { addSession(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.media_playback_channel_description)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
        try {
            val player = sharedPlayer
            player?.pause()
            player?.stop()
            player?.clearMediaItems()
        } catch (_: Exception) {}
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        mediaSession?.run {
            removeSession(this)
            release()
        }
        mediaSession = null
        sharedMediaSession = null
        sharedForwardingPlayer = null
        try {
            sharedPlayer?.stop()
            sharedPlayer?.release()
        } catch (_: Exception) {}
        sharedPlayer = null
        super.onDestroy()
    }
}
