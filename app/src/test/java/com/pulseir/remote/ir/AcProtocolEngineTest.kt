package com.pulseir.remote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcProtocolEngineTest {

    // --- Helper pulse-to-byte decoders for bit-level golden verification ---

    private fun decodeMsbBytes(pattern: List<Int>, startIdx: Int, byteCount: Int, thresholdUs: Int): IntArray {
        val out = IntArray(byteCount)
        var idx = startIdx
        for (b in 0 until byteCount) {
            var value = 0
            for (bit in 7 downTo 0) {
                // pattern[idx] = mark, pattern[idx + 1] = space
                val space = pattern[idx + 1]
                if (space > thresholdUs) {
                    value = value or (1 shl bit)
                }
                idx += 2
            }
            out[b] = value
        }
        return out
    }

    private fun decodeLsbBytes(pattern: List<Int>, startIdx: Int, byteCount: Int, thresholdUs: Int): IntArray {
        val out = IntArray(byteCount)
        var idx = startIdx
        for (b in 0 until byteCount) {
            var value = 0
            for (bit in 0 until 8) {
                val space = pattern[idx + 1]
                if (space > thresholdUs) {
                    value = value or (1 shl bit)
                }
                idx += 2
            }
            out[b] = value
        }
        return out
    }

    @Test
    fun coolix_generatesValidRepeated48BitFrameWithInvertedBytes() {
        val raw = AcProtocolEngine.encode(
            SmartAcProtocol.COOLIX,
            AcState(powerOn = true, mode = AcMode.COOL, tempC = 24, fanSpeed = AcFanSpeed.AUTO),
        )
        assertEquals(38000, raw.frequencyHz)
        // Single frame = 2 header + 48*2 bits + 1 stop = 99; 2 frames + 1 gap = 199
        assertEquals(199, raw.patternUs.size)
        assertEquals(4704, raw.patternUs[0])
        assertEquals(4416, raw.patternUs[1])
        assertTrue(raw.patternUs.all { it > 0 })

        val bytes = decodeMsbBytes(raw.patternUs, startIdx = 2, byteCount = 6, thresholdUs = 1000)
        assertEquals(0xB2, bytes[0])
        assertEquals(0xB2.inv() and 0xFF, bytes[1])
        assertEquals(0xBF, bytes[2])
        assertEquals(0xBF.inv() and 0xFF, bytes[3])
        assertEquals(0x40, bytes[4]) // 24°C -> 0x4, COOL -> 0x0
        assertEquals(0x40.inv() and 0xFF, bytes[5])
    }

    @Test
    fun coolix_swingToggleEmitsDedicatedCommand() {
        val swing = AcProtocolEngine.encode(SmartAcProtocol.COOLIX, AcState(), swingToggleOnly = true)
        val bytes = decodeMsbBytes(swing.patternUs, startIdx = 2, byteCount = 6, thresholdUs = 1000)
        assertEquals(0xB2, bytes[0])
        assertEquals(0x6B, bytes[2])
        assertEquals(0xE0, bytes[4])
    }

    @Test
    fun gree_generatesValid67BitSplitFrameAndChecksum() {
        val raw = AcProtocolEngine.encode(
            SmartAcProtocol.GREE,
            AcState(powerOn = true, mode = AcMode.COOL, tempC = 22, fanSpeed = AcFanSpeed.HIGH, swing = true),
        )
        assertEquals(38000, raw.frequencyHz)
        // 2 header + 32*2 + 3*2 + 2 inter-gap + 32*2 + 1 stop = 139
        assertEquals(139, raw.patternUs.size)
        assertEquals(9000, raw.patternUs[0])
        assertEquals(4500, raw.patternUs[1])
        assertTrue(raw.patternUs.all { it > 0 })

        val first = decodeLsbBytes(raw.patternUs, startIdx = 2, byteCount = 4, thresholdUs = 1000)
        // Second block starts after 2 header + 64 (first 4 bytes) + 6 (3 footer bits) + 2 (stop + 19980 gap) = 74
        val second = decodeLsbBytes(raw.patternUs, startIdx = 74, byteCount = 4, thresholdUs = 1000)
        val expectedChecksum = (
            (first[0] and 0x0F) + (first[1] and 0x0F) + (first[2] and 0x0F) + (first[3] and 0x0F) +
                ((second[0] ushr 4) and 0x0F) + ((second[1] ushr 4) and 0x0F) + ((second[2] ushr 4) and 0x0F) + 10
            ) and 0x0F
        assertEquals(expectedChecksum, (second[3] ushr 4) and 0x0F)
        assertEquals(22 - 16, first[1] and 0x0F)
    }

    @Test
    fun sharp_generatesValid104BitFrameWithXorNibbleChecksum() {
        val raw = AcProtocolEngine.encode(
            SmartAcProtocol.SHARP,
            AcState(powerOn = true, mode = AcMode.COOL, tempC = 23, fanSpeed = AcFanSpeed.MEDIUM, swing = true),
        )
        assertEquals(38000, raw.frequencyHz)
        // 2 header + 13*8*2 + 1 stop = 211
        assertEquals(211, raw.patternUs.size)
        assertEquals(3800, raw.patternUs[0])
        assertEquals(1900, raw.patternUs[1])
        assertTrue(raw.patternUs.all { it > 0 })

        val bytes = decodeLsbBytes(raw.patternUs, startIdx = 2, byteCount = 13, thresholdUs = 900)
        assertEquals(0xAA, bytes[0])
        assertEquals(0x5A, bytes[1])
        assertEquals(0xCF, bytes[2])
        assertEquals(0x10, bytes[3])
        assertEquals(0xC0 or (23 - 15), bytes[4])
        assertEquals(0x31, bytes[5]) // Power ON
        assertEquals((0x03 shl 4) or 0x02, bytes[6]) // Med fan + Cool mode
        assertEquals(0x07, bytes[8]) // Swing toggle

        // Verify Sharp rolling XOR nibble checksum
        var x = 0
        for (i in 0 until 12) x = x xor bytes[i]
        x = x xor (bytes[12] and 0x0F)
        val expectedNibble = (x xor (x ushr 4)) and 0x0F
        assertEquals(expectedNibble, (bytes[12] ushr 4) and 0x0F)
    }

    @Test
    fun lg_generatesValid28BitFrameAndChecksum() {
        val on = AcProtocolEngine.encode(
            SmartAcProtocol.LG,
            AcState(powerOn = true, mode = AcMode.COOL, tempC = 23, fanSpeed = AcFanSpeed.AUTO),
        )
        val off = AcProtocolEngine.encode(SmartAcProtocol.LG, AcState(powerOn = false))
        assertEquals(38000, on.frequencyHz)
        // 2 header + 28*2 + 1 stop = 59
        assertEquals(59, on.patternUs.size)
        assertEquals(59, off.patternUs.size)
        assertNotEquals(on.patternUs, off.patternUs)

        // Decode 28-bit MSB word
        var code = 0
        for (bit in 0 until 28) {
            code = (code shl 1) or if (on.patternUs[2 + bit * 2 + 1] > 1000) 1 else 0
        }
        assertEquals(0x88, (code ushr 20) and 0xFF)
        val n4 = (code ushr 16) and 0x0F
        val mode = (code ushr 12) and 0x0F
        val temp = (code ushr 8) and 0x0F
        val fan = (code ushr 4) and 0x0F
        val cs = code and 0x0F
        assertEquals((n4 + mode + temp + fan) and 0x0F, cs)
        assertEquals(23 - 15, temp)
    }

    @Test
    fun samsung_generatesValid112BitTwoSectionFrameAndBitCountChecksums() {
        val raw = AcProtocolEngine.encode(
            SmartAcProtocol.SAMSUNG,
            AcState(powerOn = true, mode = AcMode.COOL, tempC = 24, fanSpeed = AcFanSpeed.HIGH, swing = false),
        )
        assertEquals(38000, raw.frequencyHz)
        // Sec1: 2 + 112 + 1 + 1 gap = 116; Sec2: 2 + 112 + 1 = 115; total = 231
        assertEquals(231, raw.patternUs.size)
        assertEquals(3000, raw.patternUs[0])
        assertEquals(9000, raw.patternUs[1])
        assertTrue(raw.patternUs.all { it > 0 })

        val sec1 = decodeLsbBytes(raw.patternUs, startIdx = 2, byteCount = 7, thresholdUs = 1000)
        // Sec1 ON golden bytes: 02 92 0F 00 00 00 F0
        assertEquals(0x02, sec1[0])
        assertEquals(0x92, sec1[1])
        assertEquals(0x0F, sec1[2])
        assertEquals(0xF0, sec1[6])

        val sec2 = decodeLsbBytes(raw.patternUs, startIdx = 118, byteCount = 7, thresholdUs = 1000)
        assertEquals(0x01, sec2[0])
        assertEquals(0x71, sec2[3])
        assertEquals((24 - 16) shl 4, sec2[4]) // 24°C = 0x80
        assertEquals(0xF0, sec2[6])

        // Verify Samsung bit-count checksum on sec2
        val ones = Integer.bitCount(sec2[0]) +
            Integer.bitCount(sec2[1] and 0x0F) +
            Integer.bitCount(sec2[2] and 0xF0) +
            Integer.bitCount(sec2[3]) +
            Integer.bitCount(sec2[4]) +
            Integer.bitCount(sec2[5]) +
            Integer.bitCount(sec2[6])
        val expectedCs = ones.inv() and 0xFF
        val actualCs = ((sec2[1] ushr 4) and 0x0F) or ((sec2[2] and 0x0F) shl 4)
        assertEquals(expectedCs, actualCs)
    }

    @Test
    fun toshiba_generatesValid72BitStateAnd56BitSwingToggle() {
        val raw = AcProtocolEngine.encode(
            SmartAcProtocol.TOSHIBA,
            AcState(powerOn = true, mode = AcMode.HEAT, tempC = 26, fanSpeed = AcFanSpeed.HIGH),
        )
        assertEquals(38000, raw.frequencyHz)
        // Single frame = 2 header + 72*2 + 1 stop = 147; repeated + gap = 295
        assertEquals(295, raw.patternUs.size)
        assertTrue(raw.patternUs.all { it > 0 })

        val bytes = decodeMsbBytes(raw.patternUs, startIdx = 2, byteCount = 9, thresholdUs = 1000)
        assertEquals(0xF2, bytes[0])
        assertEquals(0x0D, bytes[1])
        assertEquals(0x03, bytes[2])
        assertEquals(0xFC, bytes[3])
        assertEquals(0x01, bytes[4])
        assertEquals((26 - 17) shl 4, bytes[5])
        assertEquals((0x05 shl 5) or 0x03, bytes[6]) // Max fan (5) + Heat (3)
        assertEquals(bytes[4] xor bytes[5] xor bytes[6] xor bytes[7], bytes[8])

        // Dedicated 56-bit short swing toggle command
        val swing = AcProtocolEngine.encode(SmartAcProtocol.TOSHIBA, AcState(), swingToggleOnly = true)
        // Single frame = 2 header + 56*2 + 1 stop = 115; repeated + gap = 231
        assertEquals(231, swing.patternUs.size)
        val swingBytes = decodeMsbBytes(swing.patternUs, startIdx = 2, byteCount = 7, thresholdUs = 1000)
        assertEquals(0xF2, swingBytes[0])
        assertEquals(0x0D, swingBytes[1])
        assertEquals(0x01, swingBytes[2])
        assertEquals(0xFE, swingBytes[3])
        assertEquals(0x21, swingBytes[4])
        assertEquals(0x04, swingBytes[5])
        assertEquals(0x25, swingBytes[6])
    }

    @Test
    fun daikin_generatesValid3SectionFrameWithByteSumChecksums() {
        val raw = AcProtocolEngine.encode(
            SmartAcProtocol.DAIKIN,
            AcState(powerOn = true, mode = AcMode.COOL, tempC = 24, fanSpeed = AcFanSpeed.MEDIUM, swing = true),
        )
        assertEquals(38000, raw.frequencyHz)
        // Sec1: 2 + 128 + 1 + 1 gap = 132; Sec2: 2 + 128 + 1 + 1 gap = 132; Sec3: 2 + 304 + 1 = 307; total = 571
        assertEquals(571, raw.patternUs.size)
        assertEquals(3650, raw.patternUs[0])
        assertEquals(1623, raw.patternUs[1])
        assertTrue(raw.patternUs.all { it > 0 })

        val sec1 = decodeLsbBytes(raw.patternUs, startIdx = 2, byteCount = 8, thresholdUs = 800)
        assertEquals(0x11, sec1[0])
        assertEquals(0xDA, sec1[1])
        assertEquals(0x27, sec1[2])
        assertEquals(sec1.take(7).sum() and 0xFF, sec1[7])

        val sec2 = decodeLsbBytes(raw.patternUs, startIdx = 134, byteCount = 8, thresholdUs = 800)
        assertEquals(0x42, sec2[4])
        assertEquals(sec2.take(7).sum() and 0xFF, sec2[7])

        val sec3 = decodeLsbBytes(raw.patternUs, startIdx = 266, byteCount = 19, thresholdUs = 800)
        assertEquals(0x39, sec3[5]) // COOL (3<<4) | 0x08 | ON (1)
        assertEquals(48, sec3[6]) // 24°C * 2
        assertEquals(0x5F, sec3[8]) // Medium fan (5<<4) | Swing ON (0x0F)
        assertEquals(sec3.take(18).sum() and 0xFF, sec3[18])
    }

    @Test
    fun allSmartAcProtocols_differAcrossTemperaturesPowerModesAndSwing() {
        for (proto in SmartAcProtocol.entries) {
            val s20 = AcProtocolEngine.encode(proto, AcState(powerOn = true, mode = AcMode.COOL, tempC = 20))
            val s26 = AcProtocolEngine.encode(proto, AcState(powerOn = true, mode = AcMode.COOL, tempC = 26))
            val sHeat = AcProtocolEngine.encode(proto, AcState(powerOn = true, mode = AcMode.HEAT, tempC = 26))
            val sOff = AcProtocolEngine.encode(proto, AcState(powerOn = false))
            val sSwing = AcProtocolEngine.encode(
                proto,
                AcState(powerOn = true, mode = AcMode.COOL, tempC = 20, swing = true),
                swingToggleOnly = proto.usesSeparateSwingCommand,
            )

            assertNotEquals("${proto.id} 20C vs 26C identical", s20.patternUs, s26.patternUs)
            assertNotEquals("${proto.id} COOL vs HEAT identical", s26.patternUs, sHeat.patternUs)
            assertNotEquals("${proto.id} ON vs OFF identical", s20.patternUs, sOff.patternUs)
            assertNotEquals("${proto.id} swing frame identical to normal", s20.patternUs, sSwing.patternUs)
            assertTrue("${proto.id} minTempC < maxTempC", proto.minTempC < proto.maxTempC)
        }
    }
}
