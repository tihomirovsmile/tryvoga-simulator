package space.tihsmile.trivogasim.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** All records are fictitious and local to this device. */
@Entity(tableName = "alerts", indices = [Index("status"), Index("scheduledAt")])
data class AlertEvent(
    @PrimaryKey val id: String,
    val regionId: String,
    val regionName: String,
    val type: String,
    val dangerLevel: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "SCHEDULED",
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long = 0L,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val notificationId: Int,
    val voice: String = "female",
    val language: String = "uk",
    val startNotifiedAt: Long? = null,
    val endNotifiedAt: Long? = null,
    val simulation: Boolean = true,
    val autoEndAt: Long? = null,
    val roflId: String = "",
    val permanent: Boolean = false,
    val levelChangedAt: Long? = null
)

@Entity(tableName = "timed_operations", indices = [Index("status"), Index("dueAt")])
data class TimedOperation(
    @PrimaryKey val id: String,
    val alertId: String,
    val action: String,
    val payload: String = "",
    val dueAt: Long,
    val status: String = "PENDING"
)

@Entity(tableName = "subscriptions")
data class Subscription(@PrimaryKey val regionId: String, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "regions", indices = [Index("parentId")])
data class Region(@PrimaryKey val id: String, val name: String, val type: String, val parentId: String? = null)

@Entity(tableName = "scheduled_actions")
data class ScheduledAction(@PrimaryKey val alertId: String, val dueAt: Long, val status: String = "SCHEDULED")

@Entity(tableName = "notification_history", indices = [Index("alertId")])
data class HistoryRecord(
    @PrimaryKey val id: String,
    val alertId: String,
    val action: String,
    val message: String,
    val happenedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sound_profiles")
data class SoundProfile(
    @PrimaryKey val id: String,
    val name: String,
    val language: String,
    val voice: String
)

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun watchAll(): Flow<List<AlertEvent>>

    @Query("SELECT * FROM alerts WHERE id = :id LIMIT 1")
    suspend fun get(id: String): AlertEvent?

    @Query("SELECT * FROM alerts WHERE status = 'SCHEDULED'")
    suspend fun getScheduled(): List<AlertEvent>

    @Query("SELECT * FROM alerts WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<AlertEvent>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: AlertEvent)

    @Query("UPDATE alerts SET status='DELIVERED', startedAt=:at WHERE id=:id AND status='SCHEDULED'")
    suspend fun deliver(id: String, at: Long): Int

    @Query("UPDATE alerts SET status='ACTIVE', startedAt=:at WHERE id=:id AND status='SCHEDULED'")
    suspend fun activate(id: String, at: Long): Int

    @Query("UPDATE alerts SET status='ENDED', endedAt=:at WHERE id=:id AND status='ACTIVE' AND permanent=0")
    suspend fun finish(id: String, at: Long): Int

    @Query("UPDATE alerts SET status='CANCELLED' WHERE id=:id AND status='SCHEDULED'")
    suspend fun cancel(id: String): Int

    @Query("UPDATE alerts SET dangerLevel=:level, levelChangedAt=:at WHERE id=:id AND status='ACTIVE' AND type='AIR' AND permanent=0 AND dangerLevel != :level")
    suspend fun changeLevel(id: String, level: String, at: Long): Int

    @Query("SELECT COUNT(*) FROM alerts WHERE permanent=1")
    suspend fun permanentCount(): Int

    @Query("UPDATE alerts SET startNotifiedAt=:at WHERE id=:id")
    suspend fun markStartNotified(id: String, at: Long)

    @Query("UPDATE alerts SET endNotifiedAt=:at WHERE id=:id")
    suspend fun markEndNotified(id: String, at: Long)

    @Query("DELETE FROM alerts WHERE status IN ('ENDED','CANCELLED','DELIVERED') AND permanent=0")
    suspend fun clearCompleted()
}

@Dao
interface TimedDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TimedOperation)

    @Query("SELECT * FROM timed_operations WHERE id=:id LIMIT 1")
    suspend fun get(id: String): TimedOperation?

    @Query("SELECT * FROM timed_operations WHERE status='PENDING'")
    suspend fun pending(): List<TimedOperation>

    @Query("UPDATE timed_operations SET status='DONE' WHERE id=:id AND status='PENDING'")
    suspend fun complete(id: String): Int

    @Query("UPDATE timed_operations SET status='CANCELLED' WHERE alertId=:alertId AND status='PENDING'")
    suspend fun cancelForAlert(alertId: String)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY addedAt")
    fun watch(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions ORDER BY addedAt")
    suspend fun getAll(): List<Subscription>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(s: Subscription)

    @Query("DELETE FROM subscriptions WHERE regionId=:id")
    suspend fun remove(id: String)
}

@Dao
interface RegionDao {
    @Query("SELECT * FROM regions ORDER BY name")
    suspend fun all(): List<Region>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAll(regions: List<Region>)
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(a: ScheduledAction)

    @Query("UPDATE scheduled_actions SET status=:status WHERE alertId=:id")
    suspend fun setStatus(id: String, status: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM notification_history ORDER BY happenedAt DESC")
    fun watch(): Flow<List<HistoryRecord>>

    @Insert
    suspend fun add(item: HistoryRecord)

    @Query("DELETE FROM notification_history WHERE alertId IN (SELECT id FROM alerts WHERE status IN ('ENDED','CANCELLED','DELIVERED'))")
    suspend fun clearCompleted()
}

@Dao
interface SoundDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAll(profiles: List<SoundProfile>)
}

@Database(
    entities = [AlertEvent::class, Subscription::class, Region::class,
        ScheduledAction::class, HistoryRecord::class, SoundProfile::class, TimedOperation::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timed(): TimedDao
    abstract fun alerts(): AlertDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun regions(): RegionDao
    abstract fun schedules(): ScheduleDao
    abstract fun history(): HistoryDao
    abstract fun sounds(): SoundDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: android.content.Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java,
                "simulator.db")
                .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE alerts ADD COLUMN autoEndAt INTEGER DEFAULT NULL")
                        db.execSQL("ALTER TABLE alerts ADD COLUMN roflId TEXT NOT NULL DEFAULT ''")
                        db.execSQL("ALTER TABLE alerts ADD COLUMN permanent INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE alerts ADD COLUMN levelChangedAt INTEGER DEFAULT NULL")
                        db.execSQL("CREATE TABLE IF NOT EXISTS timed_operations (id TEXT NOT NULL PRIMARY KEY, alertId TEXT NOT NULL, action TEXT NOT NULL, payload TEXT NOT NULL, dueAt INTEGER NOT NULL, status TEXT NOT NULL)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_timed_operations_status ON timed_operations(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_timed_operations_dueAt ON timed_operations(dueAt)")
                    }
                }).build().also { instance = it }
        }
    }
}
