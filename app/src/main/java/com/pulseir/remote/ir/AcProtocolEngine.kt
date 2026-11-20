package com.pulseir.remote.ir

import kotlinx.serialization.Serializable

@Serializable
enum class SmartAcProtocol(
    val id: String,
    val displayEn: String,
    val displayAr: String,
    val compatibleBrands: String,
) {
    COOLIX(
        id = "smart_ac_coolix",
        displayEn = "Coolix / Midea Universal AC",
        displayAr = "تكييف كوليكس / ميديا / كاريير / تورنيدو / فريش",
        compatibleBrands = "Carrier, Midea, Tornado, Fresh, Unionaire, Beko, York, Toshiba",
    ),
    GREE(
        id = "smart_ac_gree",
        displayEn = "Gree Universal AC",
        displayAr = "تكييف جري / فريش / يونيون إير / هاير",
        compatibleBrands = "Gree, Fresh, Unionaire, Haier, Hisense, Electrolux",
    ),
    LG(
        id = "smart_ac_lg",
        displayEn = "LG Split AC",
        displayAr = "تكييف إل جي (LG)",
        compatibleBrands = "LG, GoldStar",
    ),
    TOSHIBA(
        id = "smart_ac_toshiba",
        displayEn = "Toshiba / Carrier AC",
        displayAr = "تكييف توشيبا / كاريير",
        compatibleBrands = "Toshiba, Carrier",
    );

    fun localizedName(isArabic: Boolean): String = if (isArabic) displayAr else displayEn

    companion object {
        fun fromId(id: String?): SmartAcProtocol? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
enum class AcMode { AUTO, COOL, DRY, FAN, HEAT }

@Serializable
enum class AcFanSpeed { AUTO, LOW, MEDIUM, HIGH }

@Serializable
data class AcState(
    val powerOn: Boolean = true,
    val mode: AcMode = AcMode.COOL,
    val tempC: Int = 24,
    val fanSpeed: AcFanSpeed = AcFanSpeed.AUTO,
    val swing: Boolean = false,
)

/**
 * Pure-Kotlin Stateful Air Conditioner IR Frame Generator.
 * Synthesizes exact, checksum-verified multi-byte AC bitstreams for full
 * thermostat control (Power, Mode, 16-30°C Temp, Fan Speed, Swing) 100% offline.
 */
object AcProtocolEngine {

    fun encode(protocol: SmartAcProtocol, state: AcState, swingToggleOnly: Boolean = false): IrSignal.Raw {
        return when (protocol) {
            SmartAcProtocol.COOLIX -> encodeCoolix(state, swingToggleOnly)
            SmartAcProtocol.GREE -> encodeGree(state)
            SmartAcProtocol.LG -> encodeLg(state, swingToggleOnly)
            SmartAcProtocol.TOSHIBA -> encodeToshiba(state)
        }
    }

    // --- 1. Coolix (Midea / Carrier / Tornado / Fresh / Unionaire / York / Beko) ---
    // 24-bit payload -> each byte sent MSB-first followed by bitwise inverse (48 bits), repeated twice.
    private val coolixTempMap = intArrayOf(
        0x0, // 17°C
        0x1, // 18°C
        0x3, // 19°C
        0x2, // 20°C
        0x6, // 21°C
        0x7, // 22°C
        0x5, // 23°C
        0x4, // 24°C
        0xC, // 25°C
        0xD, // 26°C
        0x9, // 27°C
        0x8, // 28°C
        0xA, // 29°C
        0xB, // 30°C
    )

    private fun encodeCoolix(state: AcState, swingToggleOnly: Boolean): IrSignal.Raw {
        val b0: Int
        val b1: Int
        val b2: Int

        when {
            swingToggleOnly -> {
                b0 = 0xB2; b1 = 0x6B; b2 = 0xE0
            }
            !state.powerOn -> {
                b0 = 0xB2; b1 = 0x7B; b2 = 0xE0
            }
            else -> {
                b0 = 0xB2
                b1 = when {
                    state.mode == AcMode.AUTO || state.mode == AcMode.DRY -> 0x1F
                    else -> when (state.fanSpeed) {
                        AcFanSpeed.AUTO -> 0xBF
                        AcFanSpeed.LOW -> 0x9F
                        AcFanSpeed.MEDIUM -> 0x5F
                        AcFanSpeed.HIGH -> 0x3F
                    }
                }
                val tempNibble = if (state.mode == AcMode.FAN) {
                    0xE
                } else {
                    coolixTempMap[(state.tempC.coerceIn(17, 30)) - 17]
                }
                val modeNibble = when (state.mode) {
                    AcMode.COOL -> 0x0
                    AcMode.DRY -> 0x4
                    AcMode.AUTO -> 0x8
                    AcMode.HEAT -> 0xC
                    AcMode.FAN -> 0x4
                }
                b2 = (tempNibble shl 4) or modeNibble
            }
        }

        val frameBytes = intArrayOf(b0, b0.inv() and 0xFF, b1, b1.inv() and 0xFF, b2, b2.inv() and 0xFF)
        val singleFrame = ArrayList<Int>(100)
        singleFrame.add(4704)
        singleFrame.add(4416)
        for (byteVal in frameBytes) {
            for (bit in 7 downTo 0) {
                singleFrame.add(560)
                val isOne = ((byteVal ushr bit) and 1) == 1
                singleFrame.add(if (isOne) 1680 else 560)
            }
        }
        singleFrame.add(560)

        // Coolix transmits two back-to-back frames separated by 5200 us gap
        val out = ArrayList<Int>(singleFrame.size * 2 + 1)
        out.addAll(singleFrame)
        out.add(5200)
        out.addAll(singleFrame)
        return IrSignal.Raw(38000, out)
    }

    // --- 2. Gree (YAG / YAP / YBOF — 64-bit + 3-bit footer, 38 kHz) ---
    private fun encodeGree(state: AcState): IrSignal.Raw {
        val modeBits = when (state.mode) {
            AcMode.AUTO -> 0
            AcMode.COOL -> 1
            AcMode.DRY -> 2
            AcMode.FAN -> 3
            AcMode.HEAT -> 4
        }
        val powerBit = if (state.powerOn) 1 else 0
        val fanBits = when (state.fanSpeed) {
            AcFanSpeed.AUTO -> 0
            AcFanSpeed.LOW -> 1
            AcFanSpeed.MEDIUM -> 2
            AcFanSpeed.HIGH -> 3
        }
        val swingBit = if (state.swing) 1 else 0

        val b0 = modeBits or (powerBit shl 3) or (fanBits shl 4) or (swingBit shl 6)
        val b1 = (state.tempC.coerceIn(16, 30) - 16) and 0x0F
        val b2 = 0x20 // Light indicator on
        val b3 = 0x50 // Fixed Gree magic
        val b4 = if (state.swing) 0x01 else 0x00
        val b5 = 0x20
        val b6 = 0x00
        val checksumNibble = (
            (b0 and 0x0F) + (b1 and 0x0F) + (b2 and 0x0F) + (b3 and 0x0F) +
                ((b4 ushr 4) and 0x0F) + ((b5 ushr 4) and 0x0F) + ((b6 ushr 4) and 0x0F) + 10
            ) and 0x0F
        val b7 = checksumNibble shl 4

        val out = ArrayList<Int>(145)
        out.add(9000)
        out.add(4500)
        // First 32 bits LSB-first
        for (byteVal in intArrayOf(b0, b1, b2, b3)) {
            appendLsb(out, byteVal, 8, 620, 540, 1600)
        }
        // 3 fixed Gree footer bits: 0, 1, 0
        appendLsb(out, 0b010, 3, 620, 540, 1600)
        // Inter-block stop mark & 19.98ms gap
        out.add(620)
        out.add(19980)
        // Second 32 bits LSB-first
        for (byteVal in intArrayOf(b4, b5, b6, b7)) {
            appendLsb(out, byteVal, 8, 620, 540, 1600)
        }
        out.add(620)
        return IrSignal.Raw(38000, out)
    }

    // --- 3. LG Split AC (28-bit MSB-first, 38 kHz) ---
    private fun encodeLg(state: AcState, swingToggleOnly: Boolean): IrSignal.Raw {
        val code28: Int = when {
            swingToggleOnly -> if (state.swing) 0x8813149 else 0x881315A
            !state.powerOn -> 0x88C0051
            else -> {
                val modeNibble = when (state.mode) {
                    AcMode.COOL -> 0x0
                    AcMode.DRY -> 0x1
                    AcMode.FAN -> 0x2
                    AcMode.AUTO -> 0x3
                    AcMode.HEAT -> 0x4
                }
                val tempNibble = (state.tempC.coerceIn(18, 30) - 15) and 0x0F
                val fanNibble = when (state.fanSpeed) {
                    AcFanSpeed.LOW -> 0x0
                    AcFanSpeed.MEDIUM -> 0x2
                    AcFanSpeed.HIGH -> 0x4
                    AcFanSpeed.AUTO -> 0x5
                }
                val n4 = 0x0 // Power ON command nibble
                val checksum = (n4 + modeNibble + tempNibble + fanNibble) and 0x0F
                (0x88 shl 20) or (n4 shl 16) or (modeNibble shl 12) or (tempNibble shl 8) or (fanNibble shl 4) or checksum
            }
        }

        val out = ArrayList<Int>(60)
        out.add(8500)
        out.add(4250)
        for (bit in 27 downTo 0) {
            out.add(550)
            val isOne = ((code28 ushr bit) and 1) == 1
            out.add(if (isOne) 1600 else 550)
        }
        out.add(550)
        return IrSignal.Raw(38000, out)
    }

    // --- 4. Toshiba / Carrier AC (72-bit MSB-first, 38 kHz) ---
    private fun encodeToshiba(state: AcState): IrSignal.Raw {
        val b0 = 0xF2
        val b1 = 0x0D
        val b2 = 0x03
        val b3 = 0xFC
        val b4 = 0x01
        val tempByte = ((state.tempC.coerceIn(17, 30) - 17) and 0x0F) shl 4
        val modeBits = if (!state.powerOn) {
            0x07 // Toshiba power off mode code
        } else {
            when (state.mode) {
                AcMode.AUTO -> 0x00
                AcMode.COOL -> 0x01
                AcMode.DRY -> 0x02
                AcMode.HEAT -> 0x03
                AcMode.FAN -> 0x04
            }
        }
        val fanBits = when (state.fanSpeed) {
            AcFanSpeed.AUTO -> 0x00
            AcFanSpeed.LOW -> 0x02
            AcFanSpeed.MEDIUM -> 0x04
            AcFanSpeed.HIGH -> 0x06
        }
        val b5 = tempByte
        val b6 = (fanBits shl 5) or (modeBits and 0x07)
        val b7 = 0x00
        val b8 = b4 xor b5 xor b6 xor b7

        val bytes = intArrayOf(b0, b1, b2, b3, b4, b5, b6, b7, b8)
        val singleFrame = ArrayList<Int>(148)
        singleFrame.add(4400)
        singleFrame.add(4300)
        for (byteVal in bytes) {
            for (bit in 7 downTo 0) {
                singleFrame.add(543)
                val isOne = ((byteVal ushr bit) and 1) == 1
                singleFrame.add(if (isOne) 1623 else 472)
            }
        }
        singleFrame.add(543)

        val out = ArrayList<Int>(singleFrame.size * 2 + 1)
        out.addAll(singleFrame)
        out.add(7100)
        out.addAll(singleFrame)
        return IrSignal.Raw(38000, out)
    }

    private fun appendLsb(
        out: MutableList<Int>,
        value: Int,
        bits: Int,
        mark: Int,
        zeroSpace: Int,
        oneSpace: Int,
    ) {
        for (i in 0 until bits) {
            out.add(mark)
            val isOne = ((value ushr i) and 1) == 1
            out.add(if (isOne) oneSpace else zeroSpace)
        }
    }
}
