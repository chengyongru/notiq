package dev.notiq.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records")
data class NotificationRecord(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val body: String,
    val time: Long,
    val decision: String = "pending",
    val action: String = "action_pending",
    val detail: String = "",
    val probability: Double? = null,
    val confidence: Double? = null,
    val provider: String = "",
    val model: String = "",
    val rule: String = "",
    val feedback: String = "",
    @ColumnInfo(defaultValue = "''") val sourceKey: String = "",
)

@Entity(tableName = "app_rules")
data class AppRule(@PrimaryKey val packageName: String, val enabled: Boolean = false, val prompt: String = "")

@Dao
interface NotiqDao {
    @Query("SELECT * FROM records ORDER BY time DESC LIMIT 500") fun records(): Flow<List<NotificationRecord>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(record: NotificationRecord)
    @Query("UPDATE records SET decision=:decision, action=:action, detail=:detail, probability=:probability, confidence=:confidence WHERE id=:id")
    suspend fun finish(id: String, decision: String, action: String, detail: String, probability: Double?, confidence: Double?)
    @Query("UPDATE records SET feedback=:feedback WHERE id=:id") suspend fun feedback(id: String, feedback: String)
    @Query("DELETE FROM records WHERE action != 'action_relay_pending'") suspend fun clear()
    @Query("DELETE FROM records WHERE time < :before AND action != 'action_relay_pending'") suspend fun prune(before: Long)
    @Query("UPDATE records SET decision='keep', action='action_kept', detail='reason_interrupted' WHERE decision='pending' AND action != 'action_relay_pending'") suspend fun recover()
    @Query("SELECT * FROM records WHERE action = 'action_relay_pending' ORDER BY time") suspend fun pendingRelays(): List<NotificationRecord>
    @Query("SELECT * FROM app_rules") fun appRules(): Flow<List<AppRule>>
    @Query("SELECT * FROM app_rules WHERE packageName=:packageName") suspend fun rule(packageName: String): AppRule?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setRule(rule: AppRule)
}

@Database(entities = [NotificationRecord::class, AppRule::class], version = 2, exportSchema = true)
abstract class NotiqDatabase : RoomDatabase() { abstract fun dao(): NotiqDao }
