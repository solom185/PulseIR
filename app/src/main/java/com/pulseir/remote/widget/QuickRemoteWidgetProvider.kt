package com.pulseir.remote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pulseir.remote.MainActivity
import com.pulseir.remote.PulseIRApplication
import com.pulseir.remote.R
import com.pulseir.remote.ir.AcProtocolEngine
import com.pulseir.remote.ir.AcState
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.SmartAcProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickRemoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as? PulseIRApplication ?: return
        for (id in appWidgetIds) {
            app.preferences.removeWidgetDevice(id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TRANSMIT) {
            val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return
            val roleName = intent.getStringExtra(EXTRA_ROLE) ?: return
            val role = ButtonRole.fromString(roleName)
            val app = context.applicationContext as? PulseIRApplication ?: return
            val pendingResult = goAsync()

            app.appScope.launch(Dispatchers.IO) {
                try {
                    val device = app.userRepository.getDevice(deviceId) ?: return@launch
                    val smartAc = SmartAcProtocol.fromId(device.smartAcProtocolId)
                    if (smartAc != null) {
                        val current = device.acState ?: AcState()
                        val nextState = when (role) {
                            ButtonRole.POWER_OFF -> current.copy(powerOn = false)
                            ButtonRole.TEMP_DOWN -> current.copy(
                                powerOn = true,
                                tempC = (current.tempC - 1).coerceIn(smartAc.minTempC, smartAc.maxTempC),
                            )
                            ButtonRole.TEMP_UP -> current.copy(
                                powerOn = true,
                                tempC = (current.tempC + 1).coerceIn(smartAc.minTempC, smartAc.maxTempC),
                            )
                            else -> current.copy(
                                powerOn = true,
                                tempC = current.tempC.coerceIn(smartAc.minTempC, smartAc.maxTempC),
                            )
                        }
                        app.userRepository.updateDeviceAcState(device.id, nextState)
                        val signal = AcProtocolEngine.encode(smartAc, nextState)
                        val label = if (nextState.powerOn) "${device.name}: ${nextState.tempC}°C" else "${device.name}: OFF"
                        app.irTransmitter.transmit(signal, label, performHaptic = true)
                        refreshAllForDevice(app, device.id)
                        return@launch
                    }

                    val buttons = app.userRepository.getDeviceButtonsBlocking(device)
                    val target = when (role) {
                        ButtonRole.POWER -> buttons.firstOrNull { it.role == ButtonRole.POWER }
                            ?: buttons.firstOrNull { it.role == ButtonRole.POWER_ON }
                            ?: buttons.firstOrNull { it.role == ButtonRole.POWER_OFF }
                            ?: buttons.firstOrNull()
                        else -> buttons.firstOrNull { it.role == role }
                    }
                    if (target != null) {
                        app.irTransmitter.transmit(target.signal, "${device.name}: ${target.name}", performHaptic = true)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_TRANSMIT = "com.pulseir.remote.ACTION_WIDGET_TRANSMIT"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_ROLE = "extra_role"

        fun refreshAllForDevice(context: Context, deviceId: String) {
            val app = context.applicationContext as? PulseIRApplication ?: return
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, QuickRemoteWidgetProvider::class.java))
            for (widgetId in ids) {
                if (app.preferences.getWidgetDevice(widgetId) == deviceId) {
                    updateWidget(app, manager, widgetId)
                }
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val app = context.applicationContext as? PulseIRApplication
            val deviceId = app?.preferences?.getWidgetDevice(widgetId)
            val device = deviceId?.let { app.userRepository.getDevice(it) }
            val smartAc = device?.let { SmartAcProtocol.fromId(it.smartAcProtocolId) }

            val views = RemoteViews(context.packageName, R.layout.widget_quick_remote)
            val headerText = when {
                device == null -> context.getString(R.string.app_name)
                smartAc != null -> {
                    val st = device.acState ?: AcState()
                    val status = if (st.powerOn) "${st.tempC.coerceIn(smartAc.minTempC, smartAc.maxTempC)}°C" else "OFF"
                    "${device.name} · $status"
                }
                else -> device.name
            }
            views.setTextViewText(R.id.widget_title, headerText)

            // Tapping title opens the exact linked remote directly
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (device != null) {
                    putExtra(MainActivity.EXTRA_OPEN_DEVICE_ID, device.id)
                }
            }
            val openPending = PendingIntent.getActivity(
                context,
                widgetId * 10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, openPending)

            if (device != null) {
                if (smartAc != null) {
                    views.setTextViewText(R.id.widget_btn_power, "ON")
                    views.setTextViewText(R.id.widget_btn_mute, "OFF")
                    views.setTextViewText(R.id.widget_btn_vol_down, "-1°")
                    views.setTextViewText(R.id.widget_btn_vol_up, "+1°")
                    bindButton(context, views, widgetId, R.id.widget_btn_power, device.id, ButtonRole.POWER_ON, 1)
                    bindButton(context, views, widgetId, R.id.widget_btn_mute, device.id, ButtonRole.POWER_OFF, 2)
                    bindButton(context, views, widgetId, R.id.widget_btn_vol_down, device.id, ButtonRole.TEMP_DOWN, 3)
                    bindButton(context, views, widgetId, R.id.widget_btn_vol_up, device.id, ButtonRole.TEMP_UP, 4)
                } else {
                    views.setTextViewText(R.id.widget_btn_power, "⏻")
                    views.setTextViewText(R.id.widget_btn_mute, "🔇")
                    views.setTextViewText(R.id.widget_btn_vol_down, "VOL-")
                    views.setTextViewText(R.id.widget_btn_vol_up, "VOL+")
                    bindButton(context, views, widgetId, R.id.widget_btn_power, device.id, ButtonRole.POWER, 1)
                    bindButton(context, views, widgetId, R.id.widget_btn_mute, device.id, ButtonRole.MUTE, 2)
                    bindButton(context, views, widgetId, R.id.widget_btn_vol_down, device.id, ButtonRole.VOL_DOWN, 3)
                    bindButton(context, views, widgetId, R.id.widget_btn_vol_up, device.id, ButtonRole.VOL_UP, 4)
                }
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun bindButton(
            context: Context,
            views: RemoteViews,
            widgetId: Int,
            viewId: Int,
            deviceId: String,
            role: ButtonRole,
            reqOffset: Int,
        ) {
            val intent = Intent(context, QuickRemoteWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TRANSMIT
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_ROLE, role.name)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                widgetId * 10 + reqOffset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(viewId, pending)
        }
    }
}
