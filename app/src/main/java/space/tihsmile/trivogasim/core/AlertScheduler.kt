package space.tihsmile.trivogasim.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import space.tihsmile.trivogasim.data.AlertEvent
import space.tihsmile.trivogasim.data.TimedOperation

/** No Activity timers: Android holds a distinct PendingIntent per UUID. */
class AlertScheduler(private val context: Context) {
    private val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun pending(event: AlertEvent): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java).apply {
            action = "space.tihsmile.trivogasim.START"
            data = Uri.parse("trivogasim://start/${event.id}")
            putExtra("alert_id", event.id)
        }
        return PendingIntent.getBroadcast(
            context, event.notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(event: AlertEvent) {
        if (event.status != "SCHEDULED") return
        val intent = pending(event)
        try {
            if (exactAllowed()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.scheduledAt, intent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.scheduledAt, intent)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.scheduledAt, intent)
        }
    }

    private fun pendingAction(operation: TimedOperation): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java).apply {
            action = "space.tihsmile.trivogasim.TIMED_ACTION"
            data = Uri.parse("trivogasim://timed/${operation.id}")
            putExtra("timed_id", operation.id)
        }
        return PendingIntent.getBroadcast(context, operation.id.hashCode() and Int.MAX_VALUE,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleAction(operation: TimedOperation) {
        if (operation.status != "PENDING") return
        val token = pendingAction(operation)
        try {
            if (exactAllowed()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, operation.dueAt, token)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, operation.dueAt, token)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, operation.dueAt, token)
        }
    }

    fun cancelAction(operation: TimedOperation) { manager.cancel(pendingAction(operation)) }
    fun cancel(event: AlertEvent) { manager.cancel(pending(event)) }
}
