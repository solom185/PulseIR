package com.pulseir.remote.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pulseir.remote.PulseIRApplication
import com.pulseir.remote.ir.AcMode
import com.pulseir.remote.ir.AcProtocolEngine
import com.pulseir.remote.ir.AcState
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.SmartAcProtocol
import com.pulseir.remote.widget.QuickRemoteWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemoteNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RemoteNotificationController.ACTION_NOTIF_DISMISS -> {
                RemoteNotificationController.dismiss(context)
            }
            RemoteNotificationController.ACTION_NOTIF_TRANSMIT -> {
                val deviceId = intent.getStringExtra(RemoteNotificationController.EXTRA_DEVICE_ID) ?: return
                val roleName = intent.getStringExtra(RemoteNotificationController.EXTRA_ROLE) ?: return
                val acTemp = intent.getIntExtra(RemoteNotificationController.EXTRA_AC_TEMP, -1)
                val app = context.applicationContext as? PulseIRApplication ?: return
                val pendingResult = goAsync()

                app.appScope.launch(Dispatchers.IO) {
                    try {
                        val device = app.userRepository.getDevice(deviceId) ?: return@launch
                        val role = ButtonRole.fromString(roleName)

                        val smartAc = SmartAcProtocol.fromId(device.smartAcProtocolId)
                        if (smartAc != null) {
                            val current = device.acState ?: AcState()
                            val nextState = if (role == ButtonRole.POWER_OFF) {
                                current.copy(powerOn = false)
                            } else {
                                val targetTemp = if (acTemp in smartAc.minTempC..smartAc.maxTempC) {
                                    acTemp
                                } else {
                                    current.tempC.coerceIn(smartAc.minTempC, smartAc.maxTempC)
                                }
                                current.copy(powerOn = true, mode = AcMode.COOL, tempC = targetTemp)
                            }
                            app.userRepository.updateDeviceAcState(device.id, nextState)
                            val signal = AcProtocolEngine.encode(smartAc, nextState)
                            app.irTransmitter.transmit(signal, device.name, performHaptic = true)
                            QuickRemoteWidgetProvider.refreshAllForDevice(app, device.id)
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
    }
}
