package com.pulseir.remote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrEngineTest {

    @Test
    fun necStandard_encodesExactHeaderAnd67Pulses() {
        val signal = IrSignal.Parsed(IrProtocol.NEC, address = 0x04, command = 0x08)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(38000, tx.frequencyHz)
        // Header (2) + 32 bits * 2 (64) + stop mark (1) = 67
        assertEquals(67, tx.patternUs.size)
        assertEquals(9000, tx.patternUs[0])
        assertEquals(4500, tx.patternUs[1])
        // Bit 0 of addr 0x04 (00000100 LSB-first) -> 0, 0, 1, 0...
        assertEquals(560, tx.patternUs[2])
        assertEquals(560, tx.patternUs[3]) // bit 0 = 0
        assertEquals(560, tx.patternUs[5]) // bit 1 = 0
        assertEquals(1690, tx.patternUs[7]) // bit 2 = 1
        assertEquals(560, tx.patternUs.last())
    }

    @Test
    fun samsung32_repeatsAddressInsteadOfInverting() {
        val signal = IrSignal.Parsed(IrProtocol.Samsung32, address = 0x07, command = 0x02)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(38000, tx.frequencyHz)
        assertEquals(67, tx.patternUs.size)
        assertEquals(4500, tx.patternUs[0])
        assertEquals(4500, tx.patternUs[1])
        // Byte 0 (0x07) bits 0..2 are 1 -> spaces at indices 3, 5, 7 should be 1650
        assertEquals(1650, tx.patternUs[3])
        assertEquals(1650, tx.patternUs[5])
        assertEquals(1650, tx.patternUs[7])
        // Byte 1 (repeated 0x07, NOT ~0x07) starts at index 2 + 16 = 18; bit 0 space is index 19 -> 1650
        assertEquals(1650, tx.patternUs[19])
        assertEquals(1650, tx.patternUs[21])
        assertEquals(1650, tx.patternUs[23])
    }

    @Test
    fun sirc12_repeatsThreeFramesAt40kHz() {
        val signal = IrSignal.Parsed(IrProtocol.SIRC, address = 0x01, command = 0x15)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(40000, tx.frequencyHz)
        // 3 frames * (2 header + 12*2 bits) = 78
        assertEquals(78, tx.patternUs.size)
        assertEquals(2400, tx.patternUs[0])
        assertEquals(600, tx.patternUs[1])
        assertTrue(tx.patternUs.all { it > 0 })
    }

    @Test
    fun all14Protocols_produceValidPositivePulses() {
        for (proto in IrProtocol.entries) {
            val tx = IrProtocolEncoder.encode(
                IrSignal.Parsed(proto, address = 0x00200290, command = 0x00000015),
                toggleOverride = true,
            )
            assertTrue("${proto.wireName} freq out of range", tx.frequencyHz in 30000..56000)
            assertTrue("${proto.wireName} pattern too short", tx.patternUs.size >= 10)
            assertTrue("${proto.wireName} has non-positive pulse", tx.patternUs.all { it > 0 })
        }
    }

    @Test
    fun rc5_toggleChangesPattern() {
        val s = IrSignal.Parsed(IrProtocol.RC5, address = 0x00, command = 0x0C)
        val t0 = IrProtocolEncoder.encode(s, toggleOverride = false)
        val t1 = IrProtocolEncoder.encode(s, toggleOverride = true)
        assertEquals(36000, t0.frequencyHz)
        assertTrue(!t0.patternUs.contentEquals(t1.patternUs))
    }

    @Test
    fun prontoHex_parsesSamsungPowerAccurately() {
        // Classic 38kHz Pronto Hex snippet
        val pronto = "0000 006D 0004 0000 00AC 00AC 0015 0041 0015 0015 0015 0689"
        val raw = IrCodeParser.parseProntoHex(pronto)
        assertNotNull(raw)
        assertTrue(raw!!.frequencyHz in 37500..38500)
        assertEquals(8, raw.patternUs.size)
        assertTrue("Header mark ~4500us, got ${raw.patternUs[0]}", raw.patternUs[0] in 4300..4700)
    }

    @Test
    fun flipperIrFile_parsesParsedAndRawButtons() {
        val sample = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: Samsung32
            address: 07 00 00 00
            command: 02 00 00 00
            #
            name: Cool_24
            type: raw
            frequency: 38000
            duty_cycle: 0.33
            data: 9000 4500 560 560 560 1690
        """.trimIndent()

        val buttons = IrCodeParser.parseFlipperIrFile(sample)
        assertEquals(2, buttons.size)
        assertEquals(ButtonRole.POWER, buttons[0].role)
        val sig0 = buttons[0].signal as IrSignal.Parsed
        assertEquals(IrProtocol.Samsung32, sig0.protocol)
        assertEquals(7, sig0.address)
        assertEquals(2, sig0.command)

        val sig1 = buttons[1].signal as IrSignal.Raw
        assertEquals(38000, sig1.frequencyHz)
        assertEquals(listOf(9000, 4500, 560, 560, 560, 1690), sig1.patternUs)
    }

    @Test
    fun unpackRawBlob_handlesNormalAndLargeValues() {
        // 9000 (0x2328 -> 28 23), 70000 (0xFFFF + 70 11 01 00)
        val blob = byteArrayOf(
            0x28, 0x23,
            0xFF.toByte(), 0xFF.toByte(),
            0x70, 0x11, 0x01, 0x00,
        )
        val unpacked = IrProtocolEncoder.unpackRawBlob(blob)
        assertEquals(listOf(9000, 70000), unpacked)
    }
}
