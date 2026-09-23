package dev.notiq.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.notiq.NotiqApp
import dev.notiq.data.NotificationRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import java.util.UUID

class NotiqListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val permits = Semaphore(2)
    private val versions = mutableMapOf<String, String>()
    private val cancellations = mutableMapOf<Pair<String, Long>, CompletableDeferred<Boolean>>()
    private val requestedRemovals = mutableSetOf<Pair<String, Long>>()
    private val app get() = application as NotiqApp

    override fun onListenerConnected() {
        connected.value = true
        scope.launch { app.ready.await(); app.recoverRelays() }
    }
    override fun onListenerDisconnected() { connected.value = false }
    override fun onDestroy() {
        connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val ours = requestedRemovals.remove(sbn.key to sbn.postTime)
        val pending = cancellations.remove(sbn.key to sbn.postTime)
        if (pending != null) {
            pending.complete(reason == REASON_LISTENER_CANCEL)
            if (reason == REASON_LISTENER_CANCEL) return
        }
        if (ours && reason == REASON_LISTENER_CANCEL) return
        // Our own removals must not invalidate the decision that will forward the notification.
        versions.remove(sbn.key)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val version = UUID.randomUUID().toString()
        versions[sbn.key] = version
        app.processing.add(version)
        scope.launch {
            try { process(sbn, version) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.w(TAG, "Processing interrupted; pending relay remains available for recovery") }
            finally {
                app.processing.remove(version)
                if (versions[sbn.key] == version) versions.remove(sbn.key)
            }
        }
    }

    private suspend fun process(sbn: StatusBarNotification, version: String) {
        app.ready.await()
        val dao = app.database.dao()
        val selected = dao.rule(sbn.packageName) ?: return
        if (!selected.enabled) return
        val settings = app.settings.flow.first()
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val rule = selected.prompt.ifBlank { settings.prompt }
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val record = NotificationRecord(version, sbn.packageName, label, title.take(8000), body.take(16000), System.currentTimeMillis(),
            provider = settings.provider, model = settings.config.model, rule = rule, sourceKey = sbn.key)
        dao.insert(record)
        suspend fun keep(reason: String) = dao.finish(version, "keep", "action_kept", reason, null, null)
        if (title.isBlank() && body.isBlank()) { keep("reason_unreadable"); return }
        if (title.length + body.length > 6000) { keep("reason_long"); return }
        if (!sbn.isClearable || n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_TRANSPORT)) {
            keep("reason_protected"); return
        }
        if (!settings.observe && !app.relay.available()) { keep("reason_relay_permission"); return }
        val acquired = permits.tryAcquire()
        var removed = false
        try {
            if (versions[sbn.key] != version || dao.rule(sbn.packageName) != selected || app.settings.flow.first() != settings) {
                keep("reason_changed"); return
            }
            if (!settings.observe) {
                val active = activeNotifications?.firstOrNull { it.key == sbn.key }
                if (active?.postTime != sbn.postTime || !active.isClearable) { keep("reason_gone"); return }
                // Commit recovery text before taking the original out of the notification shade.
                dao.finish(version, "pending", "action_relay_pending", "", null, null)
                if (versions[sbn.key] != version || activeNotifications?.firstOrNull { it.key == sbn.key }?.postTime != sbn.postTime) {
                    keep("reason_changed"); return
                }
                if (!app.relay.available()) { keep("reason_relay_permission"); return }
                val confirmation = CompletableDeferred<Boolean>()
                val cancellationKey = sbn.key to sbn.postTime
                cancellations[cancellationKey] = confirmation
                requestedRemovals.add(cancellationKey)
                try {
                    cancelNotification(sbn.key)
                    removed = withTimeoutOrNull(750) { confirmation.await() }
                        ?: (activeNotifications?.none { it.key == sbn.key && it.postTime == sbn.postTime } == true)
                } finally { cancellations.remove(cancellationKey) }
                if (!removed) { keep("reason_not_removed"); return }
                Log.i(TAG, "Source removed; evaluating record=$version")
            }
            if (!acquired) {
                if (removed) forward(record, n, "reason_busy") else keep("reason_busy")
                return
            }
            val result = app.client.evaluate(settings.config, settings.provider, rule, "$label (${sbn.packageName})", title, body)
            val now = app.settings.flow.first()
            val currentRule = dao.rule(sbn.packageName)
            if (versions[sbn.key] != version) { keep("reason_superseded"); return }
            val shouldFilter = result.shouldFilter(settings.config.threshold)
            val unchanged = settings == now && currentRule == selected && connected.value
            val reason = when {
                !unchanged -> "reason_changed"
                result.choice == "uncertain" -> "reason_uncertain"
                result.choice == "filter" && !shouldFilter -> "reason_threshold"
                else -> "reason_keep"
            }
            if (removed) {
                if (shouldFilter && unchanged && !now.observe) {
                    dao.finish(version, "filter", "action_filtered", "reason_filter", result.probability, result.confidence)
                    Log.i(TAG, "Filtered record=$version")
                } else {
                    forward(record, n, reason, result.probability, result.confidence)
                }
            } else {
                dao.finish(version, if (shouldFilter) "filter" else "keep",
                    if (shouldFilter) "action_observed" else "action_kept",
                    if (shouldFilter) "reason_observe" else reason, result.probability, result.confidence)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val reason = when (e) {
                is java.net.SocketTimeoutException -> "reason_timeout"
                is java.io.IOException -> "reason_connection"
                else -> "reason_invalid"
            }
            if (removed && versions[sbn.key] == version) forward(record, n, reason)
            else keep(if (removed) "reason_superseded" else reason)
        } finally { if (acquired) permits.release() }
    }

    private suspend fun forward(record: NotificationRecord, source: Notification, reason: String, probability: Double? = null, confidence: Double? = null) {
        val sent = app.relay.post(record, source)
        app.database.dao().finish(record.id, "keep", if (sent) "action_forwarded" else "action_relay_pending",
            if (sent) reason else "reason_relay_permission", probability, confidence)
        Log.i(TAG, "Forwarded=$sent record=${record.id}")
    }

    companion object {
        private const val TAG = "NotiqListener"
        val connected = MutableStateFlow(false)
    }
}
