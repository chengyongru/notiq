package dev.notiq.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.notiq.NotiqApp
import dev.notiq.R
import dev.notiq.network.ConfigException
import dev.notiq.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InstalledApp(val packageName: String, val label: String, val icon: Bitmap?)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotiqApp
    val settings = app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val records = app.database.dao().records().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules = app.database.dao().appRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val testing = MutableStateFlow(false)
    init { viewModelScope.launch(Dispatchers.IO) {
        val pm = app.packageManager
        val iconSize = (38 * app.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        apps.value = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != app.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val icon = runCatching { info.loadIcon(pm).toBitmap(iconSize, iconSize) }.getOrNull()
                InstalledApp(info.activityInfo.packageName, info.loadLabel(pm).toString(), icon)
            }
            .sortedBy { it.label.lowercase() }
    } }
    private fun run(action: suspend () -> Unit) { viewModelScope.launch {
        try { action() } catch (e: CancellationException) { throw e } catch (_: Exception) { message.value = app.getString(R.string.save_failed) }
    } }
    fun observe(value: Boolean) = run { app.settings.observe(value) }
    fun provider(value: String) = run { app.settings.provider(value) }
    fun theme(value: String) = run { app.settings.theme(value) }
    fun prompt(value: String) = run { app.settings.prompt(value); message.value = app.getString(R.string.rule_saved) }
    fun appRule(rule: AppRule) = run {
        app.settings.observe(true)
        app.database.dao().setRule(rule)
    }
    fun feedback(id: String, value: String) = run { app.database.dao().feedback(id, value) }
    fun clear() = run { app.database.dao().clear() }
    fun save(provider: String, config: ServiceConfig) {
        try { app.client.validate(config, provider) }
        catch (e: Exception) { message.value = if (e is ConfigException) app.getString(e.messageRes) else app.getString(R.string.invalid_config); return }
        run { app.settings.save(provider, config); message.value = app.getString(R.string.service_saved) }
    }
    fun test(provider: String, config: ServiceConfig) {
        if (testing.value) return
        testing.value = true
        viewModelScope.launch {
            try {
                val result = app.client.evaluate(config, provider, app.getString(R.string.rule_conservative), "Notiq connection test", "促销活动", "限时优惠，领取购物优惠券")
                message.value = app.getString(R.string.connection_success, app.getString(when(result.choice) { "filter" -> R.string.choice_filter; "keep" -> R.string.choice_keep; else -> R.string.choice_uncertain }))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = if (e is ConfigException) app.getString(e.messageRes) else app.getString(R.string.connection_failed) }
            finally { testing.value = false }
        }
    }
}
