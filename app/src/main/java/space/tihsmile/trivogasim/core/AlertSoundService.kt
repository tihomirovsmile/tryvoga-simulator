package space.tihsmile.trivogasim.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import space.tihsmile.trivogasim.R

/** Playback volume is driven by the in-app slider; Android master media/mute/DND controls remain sovereign. */
class AlertSoundService : Service() {
    private var player: MediaPlayer? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("sim_playback_v3", "Звук симулятора",
            NotificationManager.IMPORTANCE_LOW).also { it.setSound(null, null) })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resource = intent?.getIntExtra("resource", 0) ?: 0
        val volume = (intent?.getFloatExtra("volume", 0.8f) ?: 0.8f).coerceIn(0f, 1f)
        val assetName = intent?.getStringExtra("asset")
        val customAudio = intent?.getStringExtra("custom_audio")
        if (resource == 0 && assetName.isNullOrBlank() && customAudio.isNullOrBlank()) { stopSelf(startId); return START_NOT_STICKY }
        val status = NotificationCompat.Builder(this, "sim_playback_v3")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("СИМУЛЯЦІЯ · Звукове повідомлення")
            .setSilent(true).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(4907, status, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(4907, status)
        player?.let { try { it.stop() } catch (_: Exception) {} ; it.release() }
        val media = MediaPlayer()
        player = media
        try {
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            if (!customAudio.isNullOrBlank()) {
                val local = CustomRoflStore.mediaPath(this, "audio", customAudio)
                    ?: error("Custom audio not found")
                media.setDataSource(local.absolutePath)
            } else if (!assetName.isNullOrBlank()) {
                val safeName = assetName.substringAfterLast('/')
                require(safeName.endsWith(".mp3"))
                // MP3 files are stored uncompressed in APK and played by file descriptor.
                // Avoid copying multi-megabyte assets on the main service thread.
                val afd = assets.openFd("alerts/rofl/audio/$safeName")
                try { media.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
                finally { afd.close() }
            } else media.setDataSource(this, android.net.Uri.parse("android.resource://$packageName/$resource"))
            media.setVolume(volume, volume)
            media.setOnCompletionListener {
                it.release(); if (player === it) player = null
                stopSelf(startId)
            }
            media.setOnErrorListener { mp, _, _ ->
                mp.release(); if (player === mp) player = null
                stopSelf(startId); true
            }
            media.setOnPreparedListener { it.start() }
            media.prepareAsync()
        } catch (_: Exception) {
            media.release(); player = null; stopSelf(startId)
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        super.onDestroy()
    }
}
