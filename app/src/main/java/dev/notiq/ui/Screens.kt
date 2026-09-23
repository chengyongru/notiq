package dev.notiq.ui

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.notiq.NotiqApp
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.notiq.data.*
import dev.notiq.notifications.NotiqListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import dev.notiq.R

@Composable
fun NotiqScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val connected by NotiqListener.connected.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    NotiqTheme(settings.theme) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    listOf(stringResource(R.string.tab_records) to Icons.Outlined.Inbox, stringResource(R.string.tab_apps) to Icons.Outlined.Apps, stringResource(R.string.tab_rules) to Icons.Outlined.Tune, stringResource(R.string.tab_settings) to Icons.Outlined.Settings).forEachIndexed { index, (name, icon) ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, null) }, label = { Text(name) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
            }
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when(tab) {
                    0 -> RecordsScreen(records, settings.observe, connected, vm, onApps = { tab = 1 })
                    1 -> AppsScreen(apps, rules, vm)
                    2 -> RulesScreen(settings.prompt, vm)
                    else -> SettingsScreen(settings, vm)
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, suffix: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
        suffix?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordsScreen(records: List<NotificationRecord>, observe: Boolean, connected: Boolean, vm: MainViewModel, onApps: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NotiqApp
    var canRemind by remember { mutableStateOf(app.relay.available()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canRemind = app.relay.available()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canRemind = app.relay.available()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val observeLabel = stringResource(R.string.observe_mode)
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<NotificationRecord?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val filtered = records.filter { filter == 0 || (filter == 1 && it.decision == "filter") || (filter == 2 && it.feedback.isNotBlank()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)) {
        item { PageTitle(stringResource(R.string.notifications), "Notiq") }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if(observe) Icons.Outlined.Visibility else Icons.Outlined.FilterAlt, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.observe_mode), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (!connected) R.string.listener_disconnected else if (observe) R.string.observe_hint else R.string.filtering_hint), style = MaterialTheme.typography.bodySmall, color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                    Switch(checked = observe, onCheckedChange = { if(it) vm.observe(true) else confirm = true }, modifier = Modifier.semantics { contentDescription = observeLabel })
                }
            }
        }
        if (!canRemind) item {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }) { Text(stringResource(R.string.allow_reminders)) }
        }
        if(!connected) item {
            Row(Modifier.fillMaxWidth().clickable {
                context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.grant_access), Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
            }
        }
        item {
            FlowRow(Modifier.padding(top = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(stringResource(R.string.all), stringResource(R.string.suggested), stringResource(R.string.reviewed)).forEachIndexed { i, name -> FilterChip(filter == i, { filter = i }, label = { Text(name) }) }
            }
        }
        if(filtered.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Inbox, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(records.isEmpty()) stringResource(R.string.waiting) else stringResource(R.string.empty_records), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                if(records.isEmpty()) TextButton(onClick = onApps) { Text(stringResource(R.string.select_apps)) }
            }
        }
        items(filtered, key = { it.id }) { record ->
            Column(Modifier.fillMaxWidth().clickable { selected = record }.padding(vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.appName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(time(record.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(record.title.ifBlank { stringResource(R.string.untitled) }, Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if(record.body.isNotBlank()) Text(record.body, Modifier.padding(top = 3.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if(record.decision == "filter") Icons.Outlined.FilterAlt else if(record.decision == "pending") Icons.Outlined.MoreHoriz else Icons.Outlined.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(recordText(record.action), Modifier.padding(start = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    if(record.feedback.isNotBlank()) Icon(Icons.Outlined.Flag, stringResource(R.string.reviewed), Modifier.padding(start = 10.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.enable_filter_title)) }, text = { Text(stringResource(R.string.enable_filter_body)) },
        confirmButton = { TextButton(onClick = { vm.observe(false); confirm = false }) { Text(stringResource(R.string.enable)) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
    selected?.let { old -> RecordDetail(records.firstOrNull { it.id == old.id } ?: old, vm) { selected = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetail(record: NotificationRecord, vm: MainViewModel, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(record.appName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(record.title.ifBlank { stringResource(R.string.untitled) }, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
            Text(record.body, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            Text(recordText(record.action), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Text(recordText(record.detail), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${record.provider} / ${record.model}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            record.probability?.let { Text(stringResource(R.string.score, it * 100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if(record.provider == "fastjev") Text(stringResource(R.string.uncalibrated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(record.feedback == "keep", { vm.feedback(record.id, if(record.feedback == "keep") "" else "keep") }, label = { Text(stringResource(R.string.should_keep)) })
                FilterChip(record.feedback == "filter", { vm.feedback(record.id, if(record.feedback == "filter") "" else "filter") }, label = { Text(stringResource(R.string.should_filter)) })
            }
        }
    }
}

@Composable
private fun AppsScreen(apps: List<InstalledApp>, rules: List<AppRule>, vm: MainViewModel) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    var edit by remember { mutableStateOf<InstalledApp?>(null) }
    var notificationSettings by remember { mutableStateOf<InstalledApp?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp)) {
        item { PageTitle(stringResource(R.string.tab_apps), stringResource(R.string.selected_count, rules.count { it.enabled })) }
        item { Text(stringResource(R.string.quiet_apps_hint), Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(bottom = 16.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text(stringResource(R.string.search_apps)) }, singleLine = true, shape = RoundedCornerShape(10.dp)) }
        items(apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }, key = { it.packageName }) { installed ->
            val rule = rules.firstOrNull { it.packageName == installed.packageName } ?: AppRule(installed.packageName)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val icon = installed.icon
                if (icon != null) {
                    Image(remember(icon) { icon.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(38.dp))
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(installed.label.take(1), style = MaterialTheme.typography.titleMedium) }
                    }
                }
                Column(Modifier.weight(1f).clickable { edit = installed }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(installed.label, style = MaterialTheme.typography.titleMedium)
                    Text(if(rule.prompt.isBlank()) stringResource(R.string.global_rule) else stringResource(R.string.custom_rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { notificationSettings = installed }) {
                    Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.app_notification_settings, installed.label))
                }
                Switch(rule.enabled, { vm.appRule(rule.copy(enabled = it)) }, modifier = Modifier.semantics { contentDescription = context.getString(R.string.filter_app, installed.label) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
        }
    }
    edit?.let { installed ->
        val rule = rules.firstOrNull { it.packageName == installed.packageName } ?: AppRule(installed.packageName)
        RuleDialog(installed.label, rule.prompt, allowEmpty = true, onSave = { vm.appRule(rule.copy(prompt = it)); edit = null }, onDismiss = { edit = null })
    }
    notificationSettings?.let { installed ->
        AlertDialog(onDismissRequest = { notificationSettings = null },
            title = { Text(installed.label) },
            text = { Text(stringResource(R.string.quiet_app_instructions)) },
            confirmButton = { TextButton(onClick = {
                notificationSettings = null
                try {
                    context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, installed.packageName))
                } catch (_: ActivityNotFoundException) {
                    try {
                        context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${installed.packageName}")))
                    } catch (_: ActivityNotFoundException) {
                        vm.message.value = context.getString(R.string.notification_settings_unavailable)
                    }
                }
            }) { Text(stringResource(R.string.open_notification_settings)) } },
            dismissButton = { TextButton(onClick = { notificationSettings = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun RulesScreen(prompt: String, vm: MainViewModel) {
    var edit by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle(stringResource(R.string.tab_rules))
        Text(stringResource(R.string.global_rule), style = MaterialTheme.typography.titleMedium)
        Surface(Modifier.fillMaxWidth().padding(top = 12.dp).clickable { edit = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(18.dp)) {
                Text(prompt, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.End) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_rule), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
            }
        }
        Text(stringResource(R.string.presets), Modifier.padding(top = 32.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium)
        Presets.items.forEach { (nameId, ruleId) ->
            val name = stringResource(nameId)
            val value = stringResource(ruleId)
            Row(Modifier.fillMaxWidth().clickable { vm.prompt(value) }.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(if(prompt == value) Icons.Outlined.Check else Icons.Outlined.ChevronRight, if(prompt == value) stringResource(R.string.selected) else null, tint = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = .5.dp)
        }
    }
    if(edit) RuleDialog(stringResource(R.string.global_rule), prompt, false, { vm.prompt(it); edit = false }, { edit = false })
}

@Composable
private fun RuleDialog(title: String, initial: String, allowEmpty: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(value, { if(it.length <= 4000) value = it }, Modifier.fillMaxWidth().heightIn(max = 320.dp), minLines = 5,
            label = { Text(if(allowEmpty) stringResource(R.string.inherit_rule) else stringResource(R.string.filter_instructions)) })
    }, confirmButton = { TextButton(onClick = { onSave(value.trim()) }, enabled = allowEmpty || value.isNotBlank()) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SettingsScreen(settings: Settings, vm: MainViewModel) {
    val context = LocalContext.current
    val thresholdLabel = stringResource(R.string.threshold)
    val testing by vm.testing.collectAsStateWithLifecycle()
    var endpoint by remember(settings.provider, settings.config) { mutableStateOf(settings.config.endpoint) }
    var model by remember(settings.provider, settings.config) { mutableStateOf(settings.config.model) }
    var key by remember(settings.provider, settings.config) { mutableStateOf(settings.config.key) }
    var threshold by remember(settings.provider, settings.config) { mutableFloatStateOf(settings.config.threshold) }
    var confirmClear by remember { mutableStateOf(false) }
    val config = ServiceConfig(endpoint, model, key, threshold)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle(stringResource(R.string.tab_settings))
        TextButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)) }) {
            Text(stringResource(R.string.reminder_settings))
        }
        Text(stringResource(R.string.service), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(settings.provider == "fastjev", { vm.provider("fastjev") }, label = { Text("FastJev") })
            FilterChip(settings.provider == "jev", { vm.provider("jev") }, label = { Text("Jev") })
        }
        OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.endpoint)) }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model)) }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.api_key)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.save(settings.provider, config) }, shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.save)) }
            TextButton(onClick = { vm.test(settings.provider, config) }, enabled = !testing) { Text(if(testing) stringResource(R.string.connecting) else stringResource(R.string.test_connection)) }
        }
        Text(stringResource(R.string.threshold), Modifier.padding(top = 28.dp), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Slider(threshold, { threshold = it }, Modifier.weight(1f).semantics { contentDescription = thresholdLabel }, valueRange = .90f..1f)
            Text("${"%.1f".format(threshold * 100)}%", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to stringResource(R.string.theme_system), "light" to stringResource(R.string.theme_light), "dark" to stringResource(R.string.theme_dark)).forEach { (value, label) -> FilterChip(settings.theme == value, { vm.theme(value) }, label = { Text(label) }) }
        }
        HorizontalDivider(Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.local_history), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.retention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear)) }
        }
        Text("Notiq 0.1.0", Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    if(confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.clear_title)) },
        confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text(stringResource(R.string.clear)) } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun recordText(value: String): String = RecordText.resource(value)?.let { stringResource(it) } ?: value

private fun time(timestamp: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
