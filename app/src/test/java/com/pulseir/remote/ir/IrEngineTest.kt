package com.pulseir.remote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun necExt_encodes16BitAddressAnd16BitCommandWithoutInversion() {
        val signal = IrSignal.Parsed(IrProtocol.NECext, address = 0x1234, command = 0x5678)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(38000, tx.frequencyHz)
        assertEquals(67, tx.patternUs.size)
        // Low byte of address = 0x34 (00110100) -> bit 2 is 1 (index 2 + 2*2 + 1 = 7)
        assertEquals(1690, tx.patternUs[7])
        // High byte of address = 0x12 (00010010) -> bit 1 is 1 (index 2 + 16 + 1*2 + 1 = 21)
        assertEquals(1690, tx.patternUs[21])
    }

    @Test
    fun nec42_encodes42BitsWith87Pulses() {
        val signal = IrSignal.Parsed(IrProtocol.NEC42, address = 0x0123, command = 0x45)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(38000, tx.frequencyHz)
        // Header (2) + 42 bits * 2 (84) + stop mark (1) = 87
        assertEquals(87, tx.patternUs.size)
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
    fun kaseikyo_encodes48BitsWith99PulsesAndPanasonicHeader() {
        val signal = IrSignal.Parsed(IrProtocol.Kaseikyo, address = 0x00200200, command = 0x0000003D)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(38000, tx.frequencyHz)
        // Header (2) + 48 bits * 2 (96) + stop (1) = 99
        assertEquals(99, tx.patternUs.size)
        assertEquals(432 * 8, tx.patternUs[0])
        assertEquals(432 * 4, tx.patternUs[1])
    }

    @Test
    fun rca_encodes24BitsWith51Pulses() {
        val signal = IrSignal.Parsed(IrProtocol.RCA, address = 0x0F, command = 0x2A)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(38000, tx.frequencyHz)
        // Header (2) + 24 bits * 2 (48) + stop (1) = 51
        assertEquals(51, tx.patternUs.size)
        assertEquals(4000, tx.patternUs[0])
        assertEquals(4000, tx.patternUs[1])
    }

    @Test
    fun pioneer_sendsRepeatedFrameAt40kHz() {
        val signal = IrSignal.Parsed(IrProtocol.Pioneer, address = 0xA5, command = 0x1C)
        val tx = IrProtocolEncoder.encode(signal)
        assertEquals(40000, tx.frequencyHz)
        // Single frame = 2 + 64 + 1 = 67; repeated + 26000 gap = 135
        assertEquals(135, tx.patternUs.size)
        assertEquals(8500, tx.patternUs[0])
        assertEquals(4225, tx.patternUs[1])
        assertEquals(26000, tx.patternUs[67])
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
    fun rc5_andRc6_toggleChangesPattern() {
        val rc5 = IrSignal.Parsed(IrProtocol.RC5, address = 0x00, command = 0x0C)
        val r5t0 = IrProtocolEncoder.encode(rc5, toggleOverride = false)
        val r5t1 = IrProtocolEncoder.encode(rc5, toggleOverride = true)
        assertEquals(36000, r5t0.frequencyHz)
        assertTrue(!r5t0.patternUs.contentEquals(r5t1.patternUs))

        val rc6 = IrSignal.Parsed(IrProtocol.RC6, address = 0x04, command = 0x10)
        val r6t0 = IrProtocolEncoder.encode(rc6, toggleOverride = false)
        val r6t1 = IrProtocolEncoder.encode(rc6, toggleOverride = true)
        assertEquals(36000, r6t0.frequencyHz)
        assertEquals(2666, r6t0.patternUs[0])
        assertTrue(!r6t0.patternUs.contentEquals(r6t1.patternUs))
    }

    @Test
    fun rc5x_encodesHighCommandBitIntoStartBit2() {
        val low = IrProtocolEncoder.encode(IrSignal.Parsed(IrProtocol.RC5X, address = 0x05, command = 0x10), toggleOverride = false)
        val high = IrProtocolEncoder.encode(IrSignal.Parsed(IrProtocol.RC5X, address = 0x05, command = 0x50), toggleOverride = false)
        assertNotEquals(low.patternUs.toList(), high.patternUs.toList())
    }

    @Test
    fun prontoHex_parsesSamsungPowerAndRejectsInvalidInputs() {
        // Classic 38kHz Pronto Hex snippet
        val pronto = "0000 006D 0004 0000 00AC 00AC 0015 0041 0015 0015 0015 0689"
        val raw = IrCodeParser.parseProntoHex(pronto)
        assertNotNull(raw)
        assertTrue(raw!!.frequencyHz in 37500..38500)
        assertEquals(8, raw.patternUs.size)
        assertTrue("Header mark ~4500us, got ${raw.patternUs[0]}", raw.patternUs[0] in 4300..4700)

        // Non-0000 format, zero freq divisor, or too few words must return null
        assertNull(IrCodeParser.parseProntoHex("5000 006D 0004 0000 0010 0010"))
        assertNull(IrCodeParser.parseProntoHex("0000 0000 0002 0000 0010 0010"))
        assertNull(IrCodeParser.parseProntoHex("0000 006D"))
        assertNull(IrCodeParser.parseProntoHex("not hex at all"))
    }

    @Test
    fun rawPattern_convertsCarrierCyclesToMicroseconds() {
        val raw = IrCodeParser.parseRawPattern(38000, "342 171 21 21", valuesAreCycles = true)
        assertNotNull(raw)
        assertEquals(38000, raw!!.frequencyHz)
        assertTrue(raw.patternUs[0] in 8900..9100)
        assertTrue(raw.patternUs[1] in 4400..4600)
        assertNull(IrCodeParser.parseRawPattern(38000, "500"))
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
    fun inferRole_mapsCommonAliasesAccurately() {
        assertEquals(ButtonRole.POWER, IrCodeParser.inferRole("Standby"))
        assertEquals(ButtonRole.VOL_UP, IrCodeParser.inferRole("Volume+"))
        assertEquals(ButtonRole.CH_DOWN, IrCodeParser.inferRole("CH_Prev"))
        assertEquals(ButtonRole.TEMP_UP, IrCodeParser.inferRole("Temp+"))
        assertEquals(ButtonRole.OTHER, IrCodeParser.inferRole("Netflix"))
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
