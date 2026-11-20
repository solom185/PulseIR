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
                val device = app.userRepository.getDevice(deviceId) ?: return
                val role = ButtonRole.fromString(roleName)

                val smartAc = SmartAcProtocol.fromId(device.smartAcProtocolId)
                if (smartAc != null) {
                    val state = if (role == ButtonRole.POWER_OFF) {
                        AcState(powerOn = false)
                    } else {
                        AcState(powerOn = true, mode = AcMode.COOL, tempC = if (acTemp in 16..30) acTemp else 24)
                    }
                    val signal = AcProtocolEngine.encode(smartAc, state)
                    app.irTransmitter.transmit(signal, device.name, performHaptic = true)
                    return
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
            }
        }
    }
}
