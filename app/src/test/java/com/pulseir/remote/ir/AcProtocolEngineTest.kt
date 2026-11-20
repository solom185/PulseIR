package com.pulseir.remote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcProtocolEngineTest {

    @Test
    fun coolix_generatesValidRepeated48BitFrame() {
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
    }

    @Test
    fun gree_generatesValid67BitSplitFrame() {
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
    }

    @Test
    fun lg_generatesValid28BitFrame() {
        val on = AcProtocolEngine.encode(SmartAcProtocol.LG, AcState(powerOn = true, mode = AcMode.COOL, tempC = 23))
        val off = AcProtocolEngine.encode(SmartAcProtocol.LG, AcState(powerOn = false))
        assertEquals(38000, on.frequencyHz)
        // 2 header + 28*2 + 1 stop = 59
        assertEquals(59, on.patternUs.size)
        assertEquals(59, off.patternUs.size)
        assertTrue(on.patternUs != off.patternUs)
    }

    @Test
    fun toshiba_generatesValidRepeated72BitFrame() {
        val raw = AcProtocolEngine.encode(SmartAcProtocol.TOSHIBA, AcState(powerOn = true, mode = AcMode.HEAT, tempC = 26))
        assertEquals(38000, raw.frequencyHz)
        // Single frame = 2 header + 72*2 + 1 stop = 147; repeated + gap = 295
        assertEquals(295, raw.patternUs.size)
        assertTrue(raw.patternUs.all { it > 0 })
    }

    @Test
    fun allSmartAcProtocols_differAcrossTemperaturesAndPower() {
        for (proto in SmartAcProtocol.entries) {
            val s20 = AcProtocolEngine.encode(proto, AcState(powerOn = true, tempC = 20))
            val s26 = AcProtocolEngine.encode(proto, AcState(powerOn = true, tempC = 26))
            val sOff = AcProtocolEngine.encode(proto, AcState(powerOn = false))
            assertTrue("${proto.id} 20C vs 26C identical", s20.patternUs != s26.patternUs)
            assertTrue("${proto.id} ON vs OFF identical", s20.patternUs != sOff.patternUs)
        }
    }
}
