package com.pulseir.remote.lockscreen

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pulseir.remote.MainActivity
import com.pulseir.remote.PulseIRApplication

/**
 * Quick Settings Tile for Android / HyperOS Control Center.
 * Opens the last used remote directly (works right over the Lock Screen!).
 */
class PulseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val app = application as? PulseIRApplication
        val latest = app?.userRepository?.state?.value?.devices?.maxByOrNull { it.lastUsedAt }
        qsTile?.apply {
            state = if (latest != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = latest?.name ?: ""
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val app = application as? PulseIRApplication
        val latest = app?.userRepository?.state?.value?.devices?.maxByOrNull { it.lastUsedAt }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (latest != null) {
                putExtra(MainActivity.EXTRA_OPEN_DEVICE_ID, latest.id)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                707,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
