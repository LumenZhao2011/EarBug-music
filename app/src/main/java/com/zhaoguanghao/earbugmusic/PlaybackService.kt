package com.zhaoguanghao.earbugmusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zhaoguanghao.earbugmusic.model.Song
import com.zhaoguanghao.earbugmusic.util.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台播放服务：系统通知栏 / 锁屏媒体控制（上一首 / 播放暂停 / 下一首）。
 */
class PlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.zhaoguanghao.earbugmusic.action.START"
        const val ACTION_TOGGLE = "com.zhaoguanghao.earbugmusic.action.TOGGLE"
        const val ACTION_NEXT = "com.zhaoguanghao.earbugmusic.action.NEXT"
        const val ACTION_PREV = "com.zhaoguanghao.earbugmusic.action.PREV"
        const val ACTION_STOP = "com.zhaoguanghao.earbugmusic.action.STOP"

        fun start(context: Context) {
            val i = Intent(context, PlaybackService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }
    }

    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cover: Bitmap? = null
    private var lastSongId: String? = null
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        PlayerCore.attachContext(applicationContext)
        createChannel()
        session = MediaSessionCompat(this, "earbug").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = PlayerCore.toggle()
                override fun onPause() = PlayerCore.toggle()
                override fun onSkipToNext() = PlayerCore.next()
                override fun onSkipToPrevious() = PlayerCore.prev()
                override fun onStop() {
                    PlayerCore.release()
                    stopSelf()
                }
            })
            isActive = true
        }
        startForegroundCompat(buildNotification(PlayerCore.state.value))

        scope.launch {
            PlayerCore.state.collect { st ->
                if (stopped) return@collect
                // 封面变化时异步加载
                val song = st.song
                if (song != null && song.id != lastSongId) {
                    lastSongId = song.id
                    cover = null
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        cover = ImageLoader.bitmapBlocking(song.coverUrl)
                    }
                }
                updateSession(st.song, st.playing)
                notify(buildNotification(st))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                PlayerCore.toggle()
                toast("已" + if (PlayerCore.state.value.playing) "播放" else "暂停")
            }
            ACTION_NEXT -> {
                PlayerCore.next()
                toast("下一首")
            }
            ACTION_PREV -> {
                PlayerCore.prev()
                toast("上一首")
            }
            ACTION_STOP -> {
                stopped = true
                PlayerCore.release()
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mgr.cancel(NOTIF_ID)
                stopForeground(true)
                stopSelf()
            }
            ACTION_START -> {
                startForegroundCompat(buildNotification(PlayerCore.state.value))
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 通知栏按钮点击后的轻提示 */
    private fun toast(msg: String) {
        try {
            android.os.Handler(mainLooper).post {
                android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        PlayerCore.release()
        super.onDestroy()
    }

    // ==================== 通知 ====================

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, "播放控制", NotificationManager.IMPORTANCE_LOW)
                ch.description = "音乐播放控制"
                ch.setShowBadge(false)
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(st: PlayerCore.State): Notification {
        val song = st.song
        val title = song?.name ?: "耳虫音乐"
        val subtitle = when {
            song == null -> "未在播放"
            st.loading -> "解析中…"
            else -> song.artist
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playIcon = if (st.playing) R.drawable.ic_pause else R.drawable.ic_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentIntent)
            .setOngoing(st.playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(R.drawable.ic_prev, "上一首", serviceIntent(ACTION_PREV))
            .addAction(playIcon, if (st.playing) "暂停" else "播放", serviceIntent(ACTION_TOGGLE))
            .addAction(R.drawable.ic_next, "下一首", serviceIntent(ACTION_NEXT))
            .addAction(R.drawable.ic_stop, "停止", serviceIntent(ACTION_STOP))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        cover?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun notify(notification: Notification) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateSession(song: Song?, playing: Boolean) {
        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song?.name ?: "耳虫音乐")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song?.artist ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, PlayerCore.state.value.durationMs)
            .build()
        session.setMetadata(meta)
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                PlayerCore.state.value.positionMs,
                1f
            )
            .build()
        session.setPlaybackState(state)
    }
}
