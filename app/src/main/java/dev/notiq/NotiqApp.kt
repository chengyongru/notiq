package dev.notiq

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.notiq.notifications.NotificationRelay
import androidx.work.*
import dev.notiq.data.*
import dev.notiq.network.DecisionClient
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotiqApp : Application() {
    val processing = ConcurrentHashMap.newKeySet<String>()
    private val recoveryMutex = Mutex()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, NotiqDatabase::class.java, "notiq.db").addMigrations(object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE records ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
        }
    }).build() }
    val settings by lazy { SettingsStore(this) }
    val client by lazy { DecisionClient() }
    val relay by lazy { NotificationRelay(this) }
    val ready by lazy { scope.async {
        recoverRelays()
        database.dao().recover()
        database.dao().prune(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
    } }
    suspend fun recoverRelays() = recoveryMutex.withLock {
        for (record in database.dao().pendingRelays()) {
            if (record.id in processing) continue
            if (relay.post(record, null)) database.dao().finish(record.id, "keep", "action_forwarded", "reason_recovered", null, null)
        }
    }
    override fun onCreate() {
        super.onCreate()
        ready
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("history-cleanup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(12, TimeUnit.HOURS).build())
    }
}

class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as NotiqApp).database.dao().prune(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
        return Result.success()
    }
}
