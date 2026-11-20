package com.pulseir.remote.ir

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure-Kotlin IR Protocol Encoder supporting all 14 standard consumer IR protocols
 * used in Flipper-IRDB plus raw microsecond timing playback.
 *
 * Output patterns always start with an ON (mark) duration in microseconds, followed
 * by alternating OFF (space) and ON (mark) durations > 0, ready for Android's ConsumerIrManager.
 */
object IrProtocolEncoder {

    private val toggleState = AtomicBoolean(false)

    fun encode(signal: IrSignal, toggleOverride: Boolean? = null): IrTransmission {
        return when (signal) {
            is IrSignal.Raw -> encodeRaw(signal)
            is IrSignal.Parsed -> encodeParsed(signal, toggleOverride)
        }
    }

    private fun encodeRaw(raw: IrSignal.Raw): IrTransmission {
        val freq = raw.frequencyHz.coerceIn(15000, 80000)
        val clean = raw.patternUs.map { it.coerceIn(10, 500_000) }
        val pattern = if (clean.isEmpty()) intArrayOf(500, 500) else clean.toIntArray()
        return IrTransmission(freq, pattern)
    }

    private fun encodeParsed(parsed: IrSignal.Parsed, toggleOverride: Boolean?): IrTransmission {
        val toggle = toggleOverride ?: toggleState.getAndSet(!toggleState.get())
        return when (parsed.protocol) {
            IrProtocol.NEC -> encodeNec(parsed.address, parsed.command)
            IrProtocol.NECext -> encodeNecExt(parsed.address, parsed.command)
            IrProtocol.NEC42 -> encodeNec42(parsed.address, parsed.command)
            IrProtocol.NEC42ext -> encodeNec42Ext(parsed.address, parsed.command)
            IrProtocol.Samsung32 -> encodeSamsung32(parsed.address, parsed.command)
            IrProtocol.RC5 -> encodeRc5(parsed.address, parsed.command, extended = false, toggle = toggle)
            IrProtocol.RC5X -> encodeRc5(parsed.address, parsed.command, extended = true, toggle = toggle)
            IrProtocol.RC6 -> encodeRc6(parsed.address, parsed.command, toggle = toggle)
            IrProtocol.SIRC -> encodeSirc(parsed.address, parsed.command, addressBits = 5)
            IrProtocol.SIRC15 -> encodeSirc(parsed.address, parsed.command, addressBits = 8)
            IrProtocol.SIRC20 -> encodeSirc(parsed.address, parsed.command, addressBits = 13)
            IrProtocol.Kaseikyo -> encodeKaseikyo(parsed.address, parsed.command)
            IrProtocol.RCA -> encodeRca(parsed.address, parsed.command)
            IrProtocol.Pioneer -> encodePioneer(parsed.address, parsed.command)
        }
    }

    // --- NEC family (38 kHz, pulse-distance, LSB-first) ---
    private fun encodeNec(address: Int, command: Int): IrTransmission {
        val a = address and 0xFF
        val aInv = a.inv() and 0xFF
        val c = command and 0xFF
        val cInv = c.inv() and 0xFF
        val bits = listOf(a, aInv, c, cInv)
        return encodePulseDistanceBytes(
            freq = 38000,
            headerMark = 9000,
            headerSpace = 4500,
            bitMark = 560,
            zeroSpace = 560,
            oneSpace = 1690,
            stopMark = 560,
            bytes = bits,
        )
    }

    private fun encodeNecExt(address: Int, command: Int): IrTransmission {
        val bits = listOf(
            address and 0xFF,
            (address ushr 8) and 0xFF,
            command and 0xFF,
            (command ushr 8) and 0xFF,
        )
        return encodePulseDistanceBytes(
            freq = 38000,
            headerMark = 9000,
            headerSpace = 4500,
            bitMark = 560,
            zeroSpace = 560,
            oneSpace = 1690,
            stopMark = 560,
            bytes = bits,
        )
    }

    private fun encodeNec42(address: Int, command: Int): IrTransmission {
        val out = ArrayList<Int>(90)
        out.add(9000)
        out.add(4500)
        appendLsbBits(out, address and 0x1FFF, 13, 560, 560, 1690)
        appendLsbBits(out, address.inv() and 0x1FFF, 13, 560, 560, 1690)
        appendLsbBits(out, command and 0xFF, 8, 560, 560, 1690)
        appendLsbBits(out, command.inv() and 0xFF, 8, 560, 560, 1690)
        out.add(560)
        return IrTransmission(38000, out.toIntArray())
    }

    private fun encodeNec42Ext(address: Int, command: Int): IrTransmission {
        val out = ArrayList<Int>(90)
        out.add(9000)
        out.add(4500)
        appendLsbBits(out, address and 0x3FFFFFF, 26, 560, 560, 1690)
        appendLsbBits(out, command and 0xFFFF, 16, 560, 560, 1690)
        out.add(560)
        return IrTransmission(38000, out.toIntArray())
    }

    // --- Samsung32 (38 kHz, 4500/4500 header, addr + addr + cmd + ~cmd) ---
    private fun encodeSamsung32(address: Int, command: Int): IrTransmission {
        val a = address and 0xFF
        val c = command and 0xFF
        val cInv = c.inv() and 0xFF
        return encodePulseDistanceBytes(
            freq = 38000,
            headerMark = 4500,
            headerSpace = 4500,
            bitMark = 550,
            zeroSpace = 550,
            oneSpace = 1650,
            stopMark = 550,
            bytes = listOf(a, a, c, cInv),
        )
    }

    // --- RC5 & RC5X (36 kHz, 888 us Manchester biphase, MSB-first, 14 bits) ---
    // Bit 1 = Space(T) + Mark(T); Bit 0 = Mark(T) + Space(T).
    private fun encodeRc5(address: Int, command: Int, extended: Boolean, toggle: Boolean): IrTransmission {
        val unit = 888
        val s1 = 1
        val s2 = if (extended) {
            if (((command ushr 6) and 1) == 1) 0 else 1
        } else {
            1
        }
        val t = if (toggle) 1 else 0
        val addr5 = address and 0x1F
        val cmd6 = command and 0x3F

        val bits = IntArray(14)
        bits[0] = s1
        bits[1] = s2
        bits[2] = t
        for (i in 0 until 5) bits[3 + i] = (addr5 ushr (4 - i)) and 1
        for (i in 0 until 6) bits[8 + i] = (cmd6 ushr (5 - i)) and 1

        // Build half-bit level stream (true = mark, false = space)
        val levels = ArrayList<Pair<Boolean, Int>>(28)
        for (b in bits) {
            if (b == 1) {
                levels.add(false to unit)
                levels.add(true to unit)
            } else {
                levels.add(true to unit)
                levels.add(false to unit)
            }
        }
        return IrTransmission(36000, mergeLevelsStartingWithMark(levels))
    }

    // --- RC6 Mode 0 (36 kHz, 444 us Manchester biphase, MSB-first) ---
    // Leader: 6T mark (2666), 2T space (889). Bit 1 = Mark+Space, Bit 0 = Space+Mark.
    // Trailer (toggle) bit is double-width (2T + 2T).
    private fun encodeRc6(address: Int, command: Int, toggle: Boolean): IrTransmission {
        val unit = 444
        val levels = ArrayList<Pair<Boolean, Int>>(60)
        levels.add(true to 2666)
        levels.add(false to 889)

        fun addBit(bit: Int, width: Int = unit) {
            if (bit == 1) {
                levels.add(true to width)
                levels.add(false to width)
            } else {
                levels.add(false to width)
                levels.add(true to width)
            }
        }

        // Start bit = 1
        addBit(1)
        // Mode = 000 (3 bits)
        addBit(0); addBit(0); addBit(0)
        // Double-width trailer/toggle bit
        addBit(if (toggle) 1 else 0, unit * 2)
        // 8-bit address MSB-first
        val a = address and 0xFF
        for (i in 7 downTo 0) addBit((a ushr i) and 1)
        // 8-bit command MSB-first
        val c = command and 0xFF
        for (i in 7 downTo 0) addBit((c ushr i) and 1)

        return IrTransmission(36000, mergeLevelsStartingWithMark(levels))
    }

    // --- Sony SIRC / SIRC15 / SIRC20 (40 kHz, pulse-width modulation, 3 repeats per spec) ---
    private fun encodeSirc(address: Int, command: Int, addressBits: Int): IrTransmission {
        val singleFrame = ArrayList<Int>((7 + addressBits) * 2 + 2)
        singleFrame.add(2400)
        singleFrame.add(600)
        val totalBits = 7 + addressBits
        val payload = (command and 0x7F) or ((address and ((1 shl addressBits) - 1)) shl 7)
        for (i in 0 until totalBits) {
            val isOne = ((payload ushr i) and 1) == 1
            singleFrame.add(if (isOne) 1200 else 600)
            singleFrame.add(600)
        }
        val frameSum = singleFrame.sum()
        val gap = (45000 - frameSum).coerceAtLeast(10000)

        // Sony hardware requires 3 repeated frames for reliable reception
        val out = ArrayList<Int>(singleFrame.size * 3)
        repeat(3) { rep ->
            out.addAll(singleFrame)
            if (rep < 2) {
                // Replace trailing 600us space with inter-frame gap
                out[out.lastIndex] = gap
            }
        }
        return IrTransmission(40000, out.toIntArray())
    }

    // --- Kaseikyo / Panasonic (38 kHz, 48-bit LSB-first, T=432 us) ---
    private fun encodeKaseikyo(address: Int, command: Int): IrTransmission {
        val id = (address ushr 24) and 0x03
        val vendorId = (address ushr 8) and 0xFFFF
        val genre1 = (address ushr 4) and 0x0F
        val genre2 = address and 0x0F

        val d0 = vendorId and 0xFF
        val d1 = (vendorId ushr 8) and 0xFF
        val vp = d0 xor d1
        val vendorParityNibble = (vp xor (vp ushr 4)) and 0x0F
        val d2 = vendorParityNibble or (genre1 shl 4)
        val d3 = genre2 or ((command and 0x0F) shl 4)
        val d4 = (id shl 6) or ((command ushr 4) and 0x3F)
        val d5 = d2 xor d3 xor d4

        return encodePulseDistanceBytes(
            freq = 38000,
            headerMark = 432 * 8,
            headerSpace = 432 * 4,
            bitMark = 432,
            zeroSpace = 432,
            oneSpace = 432 * 3,
            stopMark = 432,
            bytes = listOf(d0, d1, d2, d3, d4, d5),
        )
    }

    // --- RCA (38 kHz, 4000/4000 header, 24 bits: addr4 + cmd8 + ~addr4 + ~cmd8) ---
    private fun encodeRca(address: Int, command: Int): IrTransmission {
        val a = address and 0x0F
        val c = command and 0xFF
        val out = ArrayList<Int>(54)
        out.add(4000)
        out.add(4000)
        appendLsbBits(out, a, 4, 500, 1000, 2000)
        appendLsbBits(out, c, 8, 500, 1000, 2000)
        appendLsbBits(out, a.inv() and 0x0F, 4, 500, 1000, 2000)
        appendLsbBits(out, c.inv() and 0xFF, 8, 500, 1000, 2000)
        out.add(500)
        return IrTransmission(38000, out.toIntArray())
    }

    // --- Pioneer (40 kHz, 8500/4225 header, 32-bit NEC-style, sent twice per spec) ---
    private fun encodePioneer(address: Int, command: Int): IrTransmission {
        val a = address and 0xFF
        val c = command and 0xFF
        val frame = ArrayList<Int>(68)
        frame.add(8500)
        frame.add(4225)
        for (b in listOf(a, a.inv() and 0xFF, c, c.inv() and 0xFF)) {
            appendLsbBits(frame, b, 8, 500, 500, 1500)
        }
        frame.add(500)

        val out = ArrayList<Int>(frame.size * 2 + 1)
        out.addAll(frame)
        out.add(26000) // silence gap
        out.addAll(frame)
        return IrTransmission(40000, out.toIntArray())
    }

    private fun encodePulseDistanceBytes(
        freq: Int,
        headerMark: Int,
        headerSpace: Int,
        bitMark: Int,
        zeroSpace: Int,
        oneSpace: Int,
        stopMark: Int,
        bytes: List<Int>,
    ): IrTransmission {
        val out = ArrayList<Int>(bytes.size * 16 + 3)
        out.add(headerMark)
        out.add(headerSpace)
        for (b in bytes) {
            appendLsbBits(out, b, 8, bitMark, zeroSpace, oneSpace)
        }
        out.add(stopMark)
        return IrTransmission(freq, out.toIntArray())
    }

    private fun appendLsbBits(
        out: MutableList<Int>,
        value: Int,
        count: Int,
        bitMark: Int,
        zeroSpace: Int,
        oneSpace: Int,
    ) {
        for (i in 0 until count) {
            out.add(bitMark)
            val isOne = ((value ushr i) and 1) == 1
            out.add(if (isOne) oneSpace else zeroSpace)
        }
    }

    private fun mergeLevelsStartingWithMark(levels: List<Pair<Boolean, Int>>): IntArray {
        val merged = ArrayList<Pair<Boolean, Int>>(levels.size)
        for ((isMark, dur) in levels) {
            if (merged.isEmpty()) {
                if (!isMark) continue // ConsumerIrManager patterns must start with ON mark
                merged.add(true to dur)
            } else if (merged.last().first == isMark) {
                val prev = merged.last()
                merged[merged.lastIndex] = isMark to (prev.second + dur)
            } else {
                merged.add(isMark to dur)
            }
        }
        return IntArray(merged.size) { merged[it].second }
    }

    /**
     * Unpacks compact raw timing blobs produced by tools/build_catalog_db.py.
     * Format: uint16 LE for values < 65535; 0xFFFF followed by uint32 LE otherwise.
     */
    fun unpackRawBlob(blob: ByteArray): List<Int> {
        val out = ArrayList<Int>(blob.size / 2)
        var i = 0
        while (i + 1 < blob.size) {
            val lo = blob[i].toInt() and 0xFF
            val hi = blob[i + 1].toInt() and 0xFF
            val v = lo or (hi shl 8)
            i += 2
            if (v == 0xFFFF && i + 3 < blob.size) {
                val b0 = blob[i].toInt() and 0xFF
                val b1 = blob[i + 1].toInt() and 0xFF
                val b2 = blob[i + 2].toInt() and 0xFF
                val b3 = blob[i + 3].toInt() and 0xFF
                out.add(b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24))
                i += 4
            } else {
                out.add(v.coerceAtLeast(1))
            }
        }
        return out
    }
}
