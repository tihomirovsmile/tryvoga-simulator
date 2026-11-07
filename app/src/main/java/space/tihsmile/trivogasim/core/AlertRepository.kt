package space.tihsmile.trivogasim.core

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import space.tihsmile.trivogasim.data.*
import java.util.UUID

/** All states remain local; no connection to Ukraine's live alert systems. */
class AlertRepository(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.get(app)
    private val scheduler = AlertScheduler(app)
    private val notifier = SimNotifications(app)
    val prefs = Prefs(app)
    val alerts: Flow<List<AlertEvent>> = db.alerts().watchAll()
    val subscriptions: Flow<List<Subscription>> = db.subscriptions().watch()
    val history: Flow<List<HistoryRecord>> = db.history().watch()

    init { RegionCatalog.initialize(app) }

    suspend fun seedRegions() {
        seedPermanent()
        RegionCatalog.initialize(app)
        db.regions().addAll(RegionCatalog.regions)
        db.sounds().addAll(listOf(
            SoundProfile("uk-female", "Українська · Жіночий", "uk", "female"),
            SoundProfile("uk-male", "Українська · Чоловічий", "uk", "male"),
            SoundProfile("en-female", "English · Female", "en", "female"),
            SoundProfile("en-male", "English · Male", "en", "male")
        ))
        updateSummary()
    }

    suspend fun toggleSubscription(regionId: String): Boolean {
        val result = db.withTransaction {
            val current = db.subscriptions().getAll()
            if (current.any { it.regionId == regionId }) {
                db.subscriptions().remove(regionId); true
            } else if (current.size < 5) {
                db.subscriptions().add(Subscription(regionId)); true
            } else false
        }
        updateSummary()
        return result
    }

    suspend fun create(types: Set<AlertKind>, regionIds: Set<String>, level: String,
                       delaySeconds: Long, title: String = "", description: String = "",
                       autoEndSeconds: Long = 0L, roflId: String = "") {
        val settings = prefs.current()
        val now = System.currentTimeMillis()
        for (regionId in regionIds) {
            for (type in types) {
                require(when (type) {
                    AlertKind.AIR -> RegionCatalog.type(regionId) == "RAYON"
                    AlertKind.ARTILLERY, AlertKind.ROFL, AlertKind.INCREASED,
                    AlertKind.DRONE, AlertKind.MISSILE, AlertKind.BALLISTIC,
                    AlertKind.BOMB, AlertKind.SPECIAL, AlertKind.TECHNO -> RegionCatalog.isSettlement(regionId)
                    else -> RegionCatalog.type(regionId) in setOf("RAYON", "SETTLEMENT")
                }) { "Неприпустима територія для обраного типу сповіщення" }
                require(type != AlertKind.AIR || !RegionCatalog.isPermanentTerritory(regionId)) {
                    "Це історично безперервна тривога, її не можна змінювати"
                }
                val uuid = UUID.randomUUID().toString()
                val event = AlertEvent(
                    id = uuid, notificationId = uuid.hashCode() and Int.MAX_VALUE,
                    regionId = regionId, regionName = RegionCatalog.getName(regionId),
                    type = type.name, dangerLevel = if (type == AlertKind.AIR) level else "",
                    title = if (type == AlertKind.SPECIAL) title else if (type == AlertKind.ROFL) (CustomRoflStore.find(app, roflId)?.title ?: "Рофл тривога") else "",
                    description = if (type == AlertKind.SPECIAL) description else if (type == AlertKind.ROFL) (CustomRoflStore.find(app, roflId)?.body ?: "") else "",
                    roflId = if (type == AlertKind.ROFL) roflId else "",
                    autoEndAt = if (type.hasAllClear && autoEndSeconds > 0L) now + delaySeconds.coerceAtLeast(0)*1000L + autoEndSeconds*1000L else null,
                    createdAt = now, scheduledAt = now + delaySeconds.coerceAtLeast(0) * 1000L,
                    voice = settings.voice, language = settings.language
                )
                db.withTransaction {
                    db.alerts().insert(event)
                    db.schedules().add(ScheduledAction(event.id, event.scheduledAt))
                    if (!type.isOneShot) db.history().add(HistoryRecord(UUID.randomUUID().toString(), event.id,
                        "CREATE", "Заплановано: ${event.regionName}"))
                }
                if (delaySeconds <= 0) activate(event.id) else scheduler.schedule(event)
            }
        }
    }

    /** One-shot threat notifications transition to DELIVERED and NEVER receive an all-clear. */
    suspend fun activate(id: String) {
        val now = System.currentTimeMillis()
        val event = db.withTransaction {
            val current = db.alerts().get(id)
            if (current == null || current.status != "SCHEDULED" || current.scheduledAt > now + 500) return@withTransaction null
            val kind = AlertKind.from(current.type)
            val changed = if (kind.isOneShot) db.alerts().deliver(id, now) else db.alerts().activate(id, now)
            if (changed == 0) return@withTransaction null
            db.schedules().setStatus(id, "FIRED")
            if (!kind.isOneShot) db.history().add(HistoryRecord(UUID.randomUUID().toString(), id,
                "START", "Сповіщення надіслано", now))
            current.copy(status = if (kind.isOneShot) "DELIVERED" else "ACTIVE", startedAt = now)
        } ?: return
        if (event.autoEndAt != null && event.status == "ACTIVE") {
            scheduleOperation(event.id, "END", "", event.autoEndAt)
        }
        val subs = db.subscriptions().getAll()
        val subId = RegionCatalog.deliverySubscription(event.regionId, subs, AlertKind.from(event.type).isOneShot || event.type == "SPECIAL")
        if (subId != null && notifier.show(event, prefs.current(), ending = false, followId = subId))
            db.alerts().markStartNotified(event.id, System.currentTimeMillis())
        updateSummary()
    }

    suspend fun end(ids: Set<String>) {
        val now = System.currentTimeMillis()
        val ended = db.withTransaction {
            ids.mapNotNull { id ->
                val current = db.alerts().get(id)
                if (current == null || current.status != "ACTIVE" || current.permanent || !AlertKind.from(current.type).hasAllClear) return@mapNotNull null
                if (db.alerts().finish(id, now) == 0) return@mapNotNull null
                db.timed().cancelForAlert(id)
                db.history().add(HistoryRecord(UUID.randomUUID().toString(), id, "END", "Оголошено відбій", now))
                current.copy(status = "ENDED", endedAt = now)
            }
        }
        val subs = db.subscriptions().getAll()
        val settings = prefs.current()
        ended.forEachIndexed { index, alert ->
            val subId = RegionCatalog.deliverySubscription(alert.regionId, subs, isThreat = false)
            if (subId != null && notifier.show(alert, settings, ending = true, audible = index == 0, followId = subId))
                db.alerts().markEndNotified(alert.id, System.currentTimeMillis())
        }
        updateSummary()
    }

    suspend fun cancel(id: String) {
        val previous = db.withTransaction {
            val current = db.alerts().get(id) ?: return@withTransaction null
            if (db.alerts().cancel(id) == 0) return@withTransaction null
            db.schedules().setStatus(id, "CANCELLED")
            db.history().add(HistoryRecord(UUID.randomUUID().toString(), id, "CANCEL", "Скасовано заплановану подію"))
            current
        }
        if (previous != null) scheduler.cancel(previous)
    }

    suspend fun restore() {
        seedPermanent()
        db.timed().pending().forEach { action ->
            if (action.dueAt <= System.currentTimeMillis()) runTimedAction(action.id)
            else scheduler.scheduleAction(action)
        }
        db.alerts().getScheduled().forEach { e ->
            if (e.scheduledAt <= System.currentTimeMillis()) activate(e.id)
            else scheduler.schedule(e)
        }
        val settings = prefs.current()
        val subs = db.subscriptions().getAll()
        db.alerts().getActive().filter { it.startNotifiedAt == null }.forEach { e ->
            val subId = RegionCatalog.deliverySubscription(e.regionId, subs, false)
            if (subId != null && notifier.show(e, settings, ending = false, followId = subId))
                db.alerts().markStartNotified(e.id, System.currentTimeMillis())
        }
        updateSummary()
    }

    suspend fun updateSummary() {
        notifier.updateSummary(db.alerts().getActive(), db.subscriptions().getAll(), prefs.current())
    }

    /** Scheduled level changes and automatic all-clear survive process death and reboot. */
    suspend fun scheduleOperation(alertId: String, action: String, payload: String, dueAt: Long) {
        val op = TimedOperation(UUID.randomUUID().toString(), alertId, action, payload, dueAt)
        db.timed().insert(op)
        if (dueAt <= System.currentTimeMillis()) runTimedAction(op.id)
        else scheduler.scheduleAction(op)
    }

    suspend fun runTimedAction(id: String) {
        val op = db.withTransaction {
            val record = db.timed().get(id) ?: return@withTransaction null
            if (record.status != "PENDING" || record.dueAt > System.currentTimeMillis() + 500L) return@withTransaction null
            if (db.timed().complete(id) == 0) return@withTransaction null
            record
        } ?: return
        when (op.action) {
            "END" -> end(setOf(op.alertId))
            "LEVEL" -> changeLevel(setOf(op.alertId), op.payload)
        }
    }

    suspend fun changeLevel(ids: Set<String>, newLevel: String, delaySeconds: Long = 0L) {
        require(newLevel in setOf("YELLOW", "RED"))
        if (delaySeconds > 0L) {
            for (id in ids) {
                val existing = db.alerts().get(id) ?: continue
                if (existing.status == "ACTIVE" && existing.type == "AIR" && !existing.permanent)
                    scheduleOperation(id, "LEVEL", newLevel, System.currentTimeMillis() + delaySeconds*1000L)
            }
            return
        }
        val now = System.currentTimeMillis()
        val changed = db.withTransaction {
            ids.mapNotNull { id ->
                val event = db.alerts().get(id) ?: return@mapNotNull null
                if (db.alerts().changeLevel(id, newLevel, now) == 0) return@mapNotNull null
                db.history().add(HistoryRecord(UUID.randomUUID().toString(), id, "LEVEL",
                    if (newLevel == "RED") "Підвищено до червоного рівня" else "Знижено до жовтого рівня", now))
                event.copy(dangerLevel = newLevel, levelChangedAt = now)
            }
        }
        val subs = db.subscriptions().getAll()
        val settings = prefs.current()
        changed.forEachIndexed { idx, e ->
            val sub = RegionCatalog.deliverySubscription(e.regionId, subs, false)
            if (sub != null) notifier.showLevelChanged(e, settings, newLevel, followId = sub, audible = idx == 0)
        }
        updateSummary()
    }

    /** Historical baseline markers are never editable and do not emit initial alerts. */
    private suspend fun seedPermanent() {
        if (db.alerts().permanentCount() >= 2) return
        val entries = listOf(
            Triple("permanent_luhansk", "Луганська область", "2022-04-04T19:45:00"),
            Triple("permanent_crimea", "Автономна Республіка Крим", "2022-12-11T00:22:00")
        )
        val zone = java.time.ZoneId.of("Europe/Kyiv")
        for ((id, name, time) in entries) {
            if (db.alerts().get(id) != null) continue
            val territory = RegionCatalog.regions.firstOrNull { it.type == "OBLAST" && it.name == name } ?: continue
            val instant = java.time.LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
            db.alerts().insert(AlertEvent(id = id, notificationId = id.hashCode() and Int.MAX_VALUE,
                regionId = territory.id, regionName = territory.name, type = "AIR", dangerLevel = "RED",
                status = "ACTIVE", createdAt = instant, scheduledAt = instant, startedAt = instant,
                permanent = true, startNotifiedAt = instant))
        }
    }

    suspend fun clearCompleted() = db.withTransaction {
        db.history().clearCompleted()
        db.alerts().clearCompleted()
    }
}
