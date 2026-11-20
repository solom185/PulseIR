package com.pulseir.remote.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class IrProtocol(val wireName: String, val defaultFrequencyHz: Int) {
    NEC("NEC", 38000),
    NECext("NECext", 38000),
    NEC42("NEC42", 38000),
    NEC42ext("NEC42ext", 38000),
    Samsung32("Samsung32", 38000),
    RC5("RC5", 36000),
    RC5X("RC5X", 36000),
    RC6("RC6", 36000),
    SIRC("SIRC", 40000),
    SIRC15("SIRC15", 40000),
    SIRC20("SIRC20", 40000),
    Kaseikyo("Kaseikyo", 38000),
    RCA("RCA", 38000),
    Pioneer("Pioneer", 40000);

    companion object {
        fun fromWireName(name: String?): IrProtocol? =
            entries.firstOrNull { it.wireName.equals(name?.trim(), ignoreCase = true) }
    }
}

@Serializable
sealed interface IrSignal {
    @Serializable
    @SerialName("parsed")
    data class Parsed(
        val protocol: IrProtocol,
        val address: Int,
        val command: Int,
    ) : IrSignal {
        fun summary(): String {
            val addrHex = formatLittleEndianHex(address)
            val cmdHex = formatLittleEndianHex(command)
            return "${protocol.wireName} · A:$addrHex C:$cmdHex"
        }
    }

    @Serializable
    @SerialName("raw")
    data class Raw(
        val frequencyHz: Int,
        val patternUs: List<Int>,
    ) : IrSignal {
        fun summary(): String = "RAW · ${frequencyHz / 1000} kHz · ${patternUs.size} pulses"
    }

    fun shortBadge(): String = when (this) {
        is Parsed -> "${protocol.wireName} · ${protocol.defaultFrequencyHz / 1000} kHz"
        is Raw -> "RAW · ${frequencyHz / 1000} kHz"
    }

    companion object {
        fun formatLittleEndianHex(value: Int): String {
            val b0 = value and 0xFF
            val b1 = (value ushr 8) and 0xFF
            val b2 = (value ushr 16) and 0xFF
            val b3 = (value ushr 24) and 0xFF
            return when {
                b3 != 0 -> "%02X %02X %02X %02X".format(b0, b1, b2, b3)
                b2 != 0 -> "%02X %02X %02X".format(b0, b1, b2)
                b1 != 0 -> "%02X %02X".format(b0, b1)
                else -> "%02X".format(b0)
            }
        }
    }
}

data class IrTransmission(
    val frequencyHz: Int,
    val patternUs: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IrTransmission) return false
        return frequencyHz == other.frequencyHz && patternUs.contentEquals(other.patternUs)
    }

    override fun hashCode(): Int = 31 * frequencyHz + patternUs.contentHashCode()
}

@Serializable
enum class ButtonRole {
    POWER, POWER_ON, POWER_OFF, MUTE,
    VOL_UP, VOL_DOWN, CH_UP, CH_DOWN,
    UP, DOWN, LEFT, RIGHT, OK,
    BACK, HOME, MENU, EXIT, INFO, INPUT, GUIDE, LAST_CH,
    NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9,
    PLAY, PAUSE, STOP, REW, FF, PREV, NEXT, REC, EJECT,
    COLOR_RED, COLOR_GREEN, COLOR_YELLOW, COLOR_BLUE, COLOR_WHITE,
    BRIGHT_UP, BRIGHT_DOWN,
    TEMP_UP, TEMP_DOWN, MODE, FAN_SPEED, SWING, TIMER,
    AC_COOL, AC_HEAT, AC_DRY, AC_AUTO,
    SHUTTER, SHUTTER_2S,
    OTHER;

    companion object {
        fun fromString(value: String?): ButtonRole =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

@Serializable
data class IrButton(
    val id: Long = 0L,
    val name: String,
    val role: ButtonRole = ButtonRole.OTHER,
    val signal: IrSignal,
)
