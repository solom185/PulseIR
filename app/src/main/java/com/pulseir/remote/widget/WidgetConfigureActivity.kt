package com.pulseir.remote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulseir.remote.PulseIRApplication
import com.pulseir.remote.R
import com.pulseir.remote.ui.components.categoryAccentColor
import com.pulseir.remote.ui.components.categoryIcon
import com.pulseir.remote.ui.theme.PulseIRTheme

class WidgetConfigureActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val app = application as PulseIRApplication

        setContent {
            val settings by app.preferences.settings.collectAsState()
            val store by app.userRepository.state.collectAsState()

            PulseIRTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.widget_quick_remote_name)) })
                    },
                ) { padding ->
                    if (store.devices.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.empty_devices_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.empty_devices_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(store.devices, key = { it.id }) { device ->
                                Surface(
                                    onClick = {
                                        app.preferences.setWidgetDevice(widgetId, device.id)
                                        val mgr = AppWidgetManager.getInstance(this@WidgetConfigureActivity)
                                        QuickRemoteWidgetProvider.updateWidget(this@WidgetConfigureActivity, mgr, widgetId)
                                        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                        setResult(RESULT_OK, resultValue)
                                        finish()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            categoryIcon(device.categoryId),
                                            contentDescription = null,
                                            tint = categoryAccentColor(device.categoryId),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${device.brand} · ${device.model}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
