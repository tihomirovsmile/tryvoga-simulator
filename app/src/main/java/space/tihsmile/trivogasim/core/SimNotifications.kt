package space.tihsmile.trivogasim.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import space.tihsmile.trivogasim.MainActivity
import space.tihsmile.trivogasim.R
import space.tihsmile.trivogasim.data.AlertEvent
import space.tihsmile.trivogasim.data.RegionCatalog
import space.tihsmile.trivogasim.data.SimSettings
import space.tihsmile.trivogasim.data.Subscription
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local ordinary Android notifications. Never dispatches real emergency alerts. */
class SimNotifications(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val STATUS_ID = 0x73_54_41
        private const val STATUS_CHANNEL = "sim_status_v2"
        private val lock = Any()
    }

    private fun permitted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun mayPlay(ending: Boolean): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences("notification_audio_gate", Context.MODE_PRIVATE)
        val key = if (ending) "last_end" else "last_start"
        val last = prefs.getLong(key, -10_000L)
        val now = SystemClock.elapsedRealtime()
        if (now - last < 1800) false
        else { prefs.edit().putLong(key, now).apply(); true }
    }

    // Sound is played by AlertSoundService; notification channels are silent so
    // the device's notification/alarm sliders do not determine the track gain.
    private fun channel(ending: Boolean, vibrate: Boolean): String {
        val id = "sim_v3_${if (ending) "end" else "start"}_${if (vibrate) "v" else "n"}"
        if (manager.getNotificationChannel(id) == null) {
            val c = NotificationChannel(id, if (ending) "Відбій · симулятор" else "Тривоги · симулятор",
                NotificationManager.IMPORTANCE_HIGH)
            c.description = "Локальні тестові повідомлення (без підключення до служб оповіщення)"
            c.setSound(null, null)
            c.enableVibration(vibrate)
            manager.createNotificationChannel(c)
        }
        return id
    }

    private fun playSound(event: AlertEvent, settings: SimSettings, ending: Boolean) {
        val name = AudioCatalog.resolveName(context, event, ending)
        val resource = AudioCatalog.id(context, name)
        val rofl = if (event.type == AlertKind.ROFL.name && !ending) RoflCatalog.byId(event.roflId) else null
        if (resource == 0 && rofl == null) return
        val volume = when {
            ending -> settings.endVolume
            event.type == AlertKind.INCREASED.name -> settings.increasedVolume
            else -> settings.alertVolume
        }
        val intent = Intent(context, AlertSoundService::class.java)
            .putExtra("resource", resource).putExtra("volume", volume)
        rofl?.let { intent.putExtra("asset", it.audioFile) }
        try { ContextCompat.startForegroundService(context, intent) }
        catch (_: Exception) { /* Foreground starts may be blocked by the OS. */ }
    }

    private fun colorIcon(name: String): Bitmap? {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) return null
        val drawable = ContextCompat.getDrawable(context, id) ?: return null
        val px = (56 * context.resources.displayMetrics.density).toInt().coerceAtLeast(56)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun endLabel(kind: AlertKind): String = when (kind) {
        AlertKind.AIR -> "повітряної тривоги"
        AlertKind.ARTILLERY -> "артилерійського обстрілу"
        AlertKind.FIGHTS -> "вуличних боїв"
        AlertKind.CHEMICAL -> "хімічної загрози"
        AlertKind.RADIATION -> "радіаційної небезпеки"
        AlertKind.ROFL -> "рофл тривоги"
        else -> "спеціального повідомлення"
    }

    private fun time(event: AlertEvent, ending: Boolean): String = SimpleDateFormat("HH:mm", Locale("uk", "UA"))
        .format(Date(if (ending) event.endedAt ?: System.currentTimeMillis() else event.startedAt ?: System.currentTimeMillis()))

    fun show(event: AlertEvent, settings: SimSettings, ending: Boolean,
             audible: Boolean = true, followId: String): Boolean {
        if (!settings.notifications || !permitted()) return false
        val kind = AlertKind.from(event.type)
        if (ending && !kind.hasAllClear) return false
        // Alarm audio can be heard when the ringer is silent, subject to the OS alarm volume/DND settings.
        val play = settings.sound && audible && mayPlay(ending)

        val notificationId = if (ending) event.notificationId xor 0x40000000 else event.notificationId
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!kind.isOneShot) putExtra("open_alert_id", event.id)
            data = Uri.parse("trivogasim://details/${event.id}/${if (ending) "end" else "start"}")
        }
        val click = PendingIntent.getActivity(context, notificationId, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val label = event.title.ifBlank { kind.label }
        val title = when {
            ending -> "🟢 ${time(event, true)} — Увага! Відбій ${endLabel(kind)}"
            kind.isOneShot -> "🔴 ${time(event, false)} — $label"
            event.type == "ARTILLERY" -> "🟠 ${time(event, false)} — $label"
            event.dangerLevel == "YELLOW" -> "🟡 ${time(event, false)} — $label · Жовтий рівень"
            else -> "🔴 ${time(event, false)} — $label${if (event.dangerLevel == "RED") " · Червоний рівень" else ""}"
        }
        val followed = RegionCatalog.byId[followId]
        val placeText = when {
            followed?.type == "OBLAST" -> "Часткова ${kind.label.lowercase(Locale("uk", "UA"))} ${RegionCatalog.locationCase(followId)}. Район: ${event.regionName}"
            followed != null -> "${RegionCatalog.locationCase(followId)} · ${event.regionName}"
            else -> RegionCatalog.locationCase(event.regionId)
        }
        val message = if (ending) "${RegionCatalog.locationCase(followId)} · Відбій для ${event.regionName}" else
            "$placeText. ${if (event.description.isNotBlank()) event.description + ". " else ""}${kind.advice}"
        val icon = when {
            ending -> "ic_ok_big"
            kind == AlertKind.AIR && event.dangerLevel == "YELLOW" -> "ic_alarm_big_yellow"
            kind == AlertKind.AIR -> "ic_alarm_big"
            else -> kind.iconName
        }
        val notice = NotificationCompat.Builder(context, channel(ending, settings.vibration && play))
            .setSmallIcon(if (ending) R.drawable.ic_notification_ok else R.drawable.ic_notification)
            .setColor(if (ending) 0xFF3D8BFF.toInt() else kind.color.toInt())
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSubText("СИМУЛЯЦІЯ")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("simulator_${event.regionId}")
            .setContentIntent(click)
            .setAutoCancel(true)
        val roflImage = if (kind == AlertKind.ROFL && !ending) RoflCatalog.byId(event.roflId)?.imageFile else null
        val largeIcon = roflImage?.let { file ->
            try {
                context.assets.open("alerts/rofl/images/$file").use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    Bitmap.createScaledBitmap(bitmap, 120, 120, true)
                }
            } catch (_: Exception) { null }
        }
        (largeIcon ?: colorIcon(icon))?.let { notice.setLargeIcon(it) }
        manager.notify(notificationId, notice.build())
        if (play) playSound(event, settings, ending)
        return true
    }

    fun showLevelChanged(event: AlertEvent, settings: SimSettings, newLevel: String,
                         followId: String, audible: Boolean = true) {
        if (!settings.notifications || !permitted()) return
        val red = newLevel == "RED"
        val updatedAt = SimpleDateFormat("HH:mm", Locale("uk", "UA")).format(Date(System.currentTimeMillis()))
        val title = if (red) "🔴 $updatedAt — Підвищено рівень повітряної тривоги" else
            "🟡 $updatedAt — Знижено рівень повітряної тривоги"
        val message = "${RegionCatalog.locationCase(followId)}. ${if (red) "Червоний" else "Жовтий"} рівень"
        val builder = NotificationCompat.Builder(context, channel(false, settings.vibration))
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSubText("СИМУЛЯЦІЯ").setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        colorIcon(if (red) "ic_alarm_big" else "ic_alarm_big_yellow")?.let { builder.setLargeIcon(it) }
        manager.notify((event.notificationId xor 0x20000000) and Int.MAX_VALUE, builder.build())
        if (audible && settings.sound) playSound(event, settings, false)
    }

    /** Ongoing notification is a best effort: Android/user can still override or dismiss it. */
    fun updateSummary(active: List<AlertEvent>, follows: List<Subscription>, settings: SimSettings) {
        if (!settings.notifications || !permitted()) { manager.cancel(STATUS_ID); return }
        if (manager.getNotificationChannel(STATUS_CHANNEL) == null) {
            val ch = NotificationChannel(STATUS_CHANNEL, "Поточний стан симулятора", NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null)
            ch.description = "Зведення активних тривог на пристрої"
            manager.createNotificationChannel(ch)
        }
        val visible = active.filter { event -> RegionCatalog.deliverySubscription(event.regionId, follows, event.type == "SPECIAL") != null }
        val kind = visible.firstOrNull()?.let { AlertKind.from(it.type) }
        val text = when (visible.size) {
            0 -> "Немає тривоги"
            1 -> {
                val sub = RegionCatalog.deliverySubscription(visible[0].regionId, follows, false)!!
                val partial = RegionCatalog.type(sub) == "OBLAST"
                "${if (partial) "Часткова " else ""}${kind!!.label.lowercase(Locale("uk", "UA"))} ${RegionCatalog.locationCase(sub)}"
            }
            else -> "Активно декілька тривог (${visible.size})"
        }
        val details = if (visible.isEmpty()) "Локальні тривоги відсутні" else
            visible.take(8).joinToString("; ") { "${AlertKind.from(it.type).label}: ${it.regionName}" }
        val intent = PendingIntent.getActivity(context, STATUS_ID, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(if (visible.isEmpty()) R.drawable.ic_notification_ok else R.drawable.ic_notification)
            .setColor(if (visible.isEmpty()) 0xFF3D8BFF.toInt() else kind!!.color.toInt())
            .setContentTitle(if (visible.size > 1) "Активно декілька тривог" else text)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setSubText("СИМУЛЯЦІЯ")
            .setContentIntent(intent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        colorIcon(when {
            visible.isEmpty() -> "ic_ok_big"
            visible.size > 1 -> "ic_alarm_big"
            kind == AlertKind.AIR && visible[0].dangerLevel == "YELLOW" -> "ic_alarm_big_yellow"
            kind == AlertKind.AIR -> "ic_alarm_big"
            else -> kind!!.iconName
        })?.let { builder.setLargeIcon(it) }
        val statusNotice = builder.build()
        statusNotice.flags = statusNotice.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        manager.notify(STATUS_ID, statusNotice)
    }

    fun dismissSummary() { manager.cancel(STATUS_ID) }
}
