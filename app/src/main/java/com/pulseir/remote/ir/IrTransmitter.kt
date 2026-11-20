package com.pulseir.remote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

data class TransmitPulse(
    val timestampMs: Long,
    val label: String,
    val badge: String,
    val transmittedHardware: Boolean,
)

enum class HapticLevel(val durationMs: Long, val amplitude: Int) {
    OFF(0L, 0),
    LIGHT(12L, 60),
    MEDIUM(22L, 128),
    STRONG(35L, 210);

    companion object {
        fun fromInt(ordinal: Int): HapticLevel = entries.getOrElse(ordinal) { MEDIUM }
    }
}

/**
 * Thread-safe hardware IR Blaster controller backed by Android's ConsumerIrManager.
 * Serializes all bursts on a dedicated single-thread executor so rapid clicks or
 * hold-to-repeat gestures never collide on the IR LED.
 */
class IrTransmitter(context: Context) {
    private val appContext = context.applicationContext
    private val irManager: ConsumerIrManager? =
        runCatching { appContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager }.getOrNull()

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PulseIR-Blaster").apply { isDaemon = true }
    }

    @Volatile
    var hapticLevel: HapticLevel = HapticLevel.MEDIUM

    private val _lastPulse = MutableStateFlow<TransmitPulse?>(null)
    val lastPulse: StateFlow<TransmitPulse?> = _lastPulse.asStateFlow()

    val hasIrEmitter: Boolean by lazy {
        runCatching { irManager?.hasIrEmitter() == true }.getOrDefault(false)
    }

    val carrierFrequencyRanges: List<IntRange> by lazy {
        runCatching {
            irManager?.carrierFrequencies?.map { it.minFrequency..it.maxFrequency } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun transmit(signal: IrSignal, label: String = "", performHaptic: Boolean = true) {
        if (performHaptic) vibrate()
        val encoded = IrProtocolEncoder.encode(signal)
        val badge = signal.shortBadge()
        executor.execute {
            val sent = transmitBlockingInternal(encoded)
            _lastPulse.value = TransmitPulse(
                timestampMs = System.currentTimeMillis(),
                label = label,
                badge = badge,
                transmittedHardware = sent,
            )
        }
    }

    /**
     * Synchronous transmit for Macros and AppWidgets.
     */
    fun transmitBlocking(signal: IrSignal, label: String = "", performHaptic: Boolean = false): Boolean {
        if (performHaptic) vibrate()
        val encoded = IrProtocolEncoder.encode(signal)
        val sent = transmitBlockingInternal(encoded)
        _lastPulse.value = TransmitPulse(
            timestampMs = System.currentTimeMillis(),
            label = label,
            badge = signal.shortBadge(),
            transmittedHardware = sent,
        )
        return sent
    }

    /**
     * Triggers haptic feedback only without firing the IR LED (for Settings preview).
     */
    fun testHaptic() {
        vibrate()
    }

    /**
     * Sends a visible 38 kHz diagnostic pulse (~200 ms burst) for checking the IR LED
     * through another smartphone camera.
     */
    fun sendDiagnosticPulse() {
        vibrate()
        val pattern = IntArray(40) { i -> if (i % 2 == 0) 4000 else 1000 }
        val tx = IrTransmission(38000, pattern)
        executor.execute {
            val sent = transmitBlockingInternal(tx)
            _lastPulse.value = TransmitPulse(
                timestampMs = System.currentTimeMillis(),
                label = "38 kHz Test Pulse",
                badge = "TEST · 38 kHz",
                transmittedHardware = sent,
            )
        }
    }

    private fun transmitBlockingInternal(tx: IrTransmission): Boolean {
        val mgr = irManager ?: return false
        if (!hasIrEmitter) return false
        return runCatching {
            mgr.transmit(tx.frequencyHz, tx.patternUs)
            true
        }.getOrDefault(false)
    }

    private fun vibrate() {
        val level = hapticLevel
        if (level == HapticLevel.OFF) return
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(level.durationMs, level.amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(level.durationMs)
            }
        }
    }
}
