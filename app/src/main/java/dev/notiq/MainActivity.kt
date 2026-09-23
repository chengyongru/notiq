package dev.notiq

import android.os.Bundle
import android.os.Build
import kotlinx.coroutines.launch
import android.app.NotificationManager
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.notiq.ui.NotiqScreen
import dev.notiq.notifications.NotiqListener

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NotiqScreen(viewModel()) }
    }

    override fun onResume() {
        super.onResume()
        val app = application as NotiqApp
        app.scope.launch { app.ready.await(); app.recoverRelays() }
        val listener = ComponentName(this, NotiqListener::class.java)
        if (!NotiqListener.connected.value && getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)) {
            // Clear a stale binding after a force-stop before asking Android to reconnect.
            if (Build.VERSION.SDK_INT >= 34) NotificationListenerService.requestUnbind(listener)
            NotificationListenerService.requestRebind(listener)
        }
    }
}
