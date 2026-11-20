package com.pulseir.remote.lockscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.pulseir.remote.MainActivity
import com.pulseir.remote.R
import com.pulseir.remote.data.AppPreferences
import com.pulseir.remote.data.SavedDevice
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.SmartAcProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the Mi-Remote-style persistent Lock Screen & Notification Shade mini-remote.
 * Uses VISIBILITY_PUBLIC so controls work directly from the Lock Screen without unlocking.
 */
object RemoteNotificationController {
    private const val CHANNEL_ID = "pulseir_lockscreen_remote"
    private const val NOTIFICATION_ID = 7001

    const val ACTION_NOTIF_TRANSMIT = "com.pulseir.remote.ACTION_NOTIF_TRANSMIT"
    const val ACTION_NOTIF_DISMISS = "com.pulseir.remote.ACTION_NOTIF_DISMISS"
    const val EXTRA_DEVICE_ID = "extra_device_id"
    const val EXTRA_ROLE = "extra_role"
    const val EXTRA_AC_TEMP = "extra_ac_temp"

    @Volatile
    private var appPreferences: AppPreferences? = null

    private val _pinnedDeviceId = MutableStateFlow<String?>(null)
    val pinnedDeviceId: StateFlow<String?> = _pinnedDeviceId.asStateFlow()

    fun init(preferences: AppPreferences) {
        appPreferences = preferences
        _pinnedDeviceId.value = preferences.getPinnedNotificationDeviceId()
    }

    fun pinRemote(context: Context, device: SavedDevice) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, mgr)

        val isSmartAc = SmartAcProtocol.fromId(device.smartAcProtocolId) != null
        val views = RemoteViews(context.packageName, R.layout.notif_lockscreen_remote)
        views.setTextViewText(R.id.notif_title, device.name)

        // Open exact device directly on tap
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_DEVICE_ID, device.id)
        }
        val openPending = PendingIntent.getActivity(
            context,
            900,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.notif_title, openPending)

        // Close button
        val dismissIntent = Intent(context, RemoteNotificationReceiver::class.java).apply {
            action = ACTION_NOTIF_DISMISS
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            901,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.notif_btn_close, dismissPending)

        if (isSmartAc) {
            // Adaptive Smart AC Lock-Screen bar: ON / OFF / 20° / 22° / 24° / 26°
            views.setTextViewText(R.id.notif_btn_power, "ON")
            views.setTextViewText(R.id.notif_btn_mute, "OFF")
            views.setTextViewText(R.id.notif_btn_vol_down, "20°")
            views.setTextViewText(R.id.notif_btn_vol_up, "22°")
            views.setTextViewText(R.id.notif_btn_ch_down, "24°")
            views.setTextViewText(R.id.notif_btn_ch_up, "26°")

            bindAcAction(context, views, R.id.notif_btn_power, device.id, ButtonRole.POWER_ON, 24, 910)
            bindAcAction(context, views, R.id.notif_btn_mute, device.id, ButtonRole.POWER_OFF, 0, 911)
            bindAcAction(context, views, R.id.notif_btn_vol_down, device.id, ButtonRole.POWER_ON, 20, 912)
            bindAcAction(context, views, R.id.notif_btn_vol_up, device.id, ButtonRole.POWER_ON, 22, 913)
            bindAcAction(context, views, R.id.notif_btn_ch_down, device.id, ButtonRole.POWER_ON, 24, 914)
            bindAcAction(context, views, R.id.notif_btn_ch_up, device.id, ButtonRole.POWER_ON, 26, 915)
        } else {
            // Standard TV / Receiver / AV Lock-Screen bar: Power / Mute / VOL- / VOL+ / CH- / CH+
            bindRoleAction(context, views, R.id.notif_btn_power, device.id, ButtonRole.POWER, 910)
            bindRoleAction(context, views, R.id.notif_btn_mute, device.id, ButtonRole.MUTE, 911)
            bindRoleAction(context, views, R.id.notif_btn_vol_down, device.id, ButtonRole.VOL_DOWN, 912)
            bindRoleAction(context, views, R.id.notif_btn_vol_up, device.id, ButtonRole.VOL_UP, 913)
            bindRoleAction(context, views, R.id.notif_btn_ch_down, device.id, ButtonRole.CH_DOWN, 914)
            bindRoleAction(context, views, R.id.notif_btn_ch_up, device.id, ButtonRole.CH_UP, 915)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_remote)
            .setContentTitle(device.name)
            .setContentIntent(openPending)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            mgr.notify(NOTIFICATION_ID, notification)
            _pinnedDeviceId.value = device.id
            appPreferences?.setPinnedNotificationDeviceId(device.id)
        }
    }

    fun dismiss(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(NOTIFICATION_ID)
        _pinnedDeviceId.value = null
        appPreferences?.setPinnedNotificationDeviceId(null)
    }

    private fun ensureChannel(context: Context, mgr: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.lockscreen_remote_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun bindRoleAction(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        deviceId: String,
        role: ButtonRole,
        reqCode: Int,
    ) {
        val intent = Intent(context, RemoteNotificationReceiver::class.java).apply {
            action = ACTION_NOTIF_TRANSMIT
            putExtra(EXTRA_DEVICE_ID, deviceId)
            putExtra(EXTRA_ROLE, role.name)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(viewId, pi)
    }

    private fun bindAcAction(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        deviceId: String,
        role: ButtonRole,
        tempC: Int,
        reqCode: Int,
    ) {
        val intent = Intent(context, RemoteNotificationReceiver::class.java).apply {
            action = ACTION_NOTIF_TRANSMIT
            putExtra(EXTRA_DEVICE_ID, deviceId)
            putExtra(EXTRA_ROLE, role.name)
            putExtra(EXTRA_AC_TEMP, tempC)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(viewId, pi)
    }
}
