package com.pulseir.remote.ir

import kotlin.math.roundToInt

/**
 * Parses Pronto Hex codes, Raw microsecond/cycle strings, Protocol hex fields,
 * and standard Flipper Zero .ir files completely offline.
 */
object IrCodeParser {

    /**
     * Parses standard Pronto Hex (format 0000 FREQ_DIV SEQ1_PAIRS SEQ2_PAIRS data...).
     */
    fun parseProntoHex(input: String): IrSignal.Raw? {
        val tokens = input.trim().split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
        if (tokens.size < 6) return null
        val words = tokens.mapNotNull { it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) }
        if (words.size != tokens.size) return null

        val formatCode = words[0]
        if (formatCode != 0) return null // Only standard learned Pronto Hex (0000)
        val freqDivisor = words[1]
        if (freqDivisor <= 0) return null

        val freqHz = (1_000_000.0 / (freqDivisor * 0.241246)).roundToInt().coerceIn(15000, 80000)
        val oncePairs = words[2]
        val repeatPairs = words[3]
        val expectedBurstCount = (oncePairs + repeatPairs) * 2
        val availableBursts = words.size - 4
        if (availableBursts < 2) return null

        val count = if (expectedBurstCount in 2..availableBursts) expectedBurstCount else availableBursts
        val periodUs = 1_000_000.0 / freqHz
        val timings = ArrayList<Int>(count)
        for (i in 0 until count) {
            val cycles = words[4 + i]
            val us = (cycles * periodUs).roundToInt().coerceIn(10, 500_000)
            timings.add(us)
        }
        return IrSignal.Raw(freqHz, timings)
    }

    /**
     * Parses raw decimal timing strings (either microseconds or carrier cycle counts).
     * Also accepts formats where the first number is frequency if frequencyHz <= 0.
     */
    fun parseRawPattern(
        frequencyHz: Int,
        patternText: String,
        valuesAreCycles: Boolean = false,
    ): IrSignal.Raw? {
        val nums = patternText.trim()
            .split(Regex("[\\s,;]+"))
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 0 }
        if (nums.size < 2) return null

        val freq = frequencyHz.coerceIn(15000, 80000)
        val timings = if (valuesAreCycles) {
            val periodUs = 1_000_000.0 / freq
            nums.map { (it * periodUs).roundToInt().coerceIn(10, 500_000) }
        } else {
            nums.map { it.coerceIn(10, 500_000) }
        }
        return IrSignal.Raw(freq, timings)
    }

    /**
     * Parses either Flipper little-endian spaced hex bytes (e.g. "01 72 00 00")
     * or a standard hex string (e.g. "07" or "0x07" or "7201").
     */
    fun parseHexAddressOrCommand(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(Regex("\\s+"))
        return if (parts.size > 1) {
            var acc = 0
            for (i in parts.indices.take(4)) {
                val b = parts[i].removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return null
                acc = acc or ((b and 0xFF) shl (8 * i))
            }
            acc
        } else {
            trimmed.removePrefix("0x").removePrefix("0X").toLongOrNull(16)?.toInt()
        }
    }

    /**
     * Parses a complete Flipper Zero .ir file into a list of ready-to-use IrButtons.
     */
    fun parseFlipperIrFile(content: String): List<IrButton> {
        val result = ArrayList<IrButton>()
        val cur = HashMap<String, String>()
        var nextId = 1L

        fun flush() {
            val name = cur["name"]?.trim()
            val type = cur["type"]?.trim()?.lowercase()
            if (!name.isNullOrEmpty() && !type.isNullOrEmpty()) {
                when (type) {
                    "parsed" -> {
                        val proto = IrProtocol.fromWireName(cur["protocol"])
                        val addr = cur["address"]?.let { parseHexAddressOrCommand(it) }
                        val cmd = cur["command"]?.let { parseHexAddressOrCommand(it) }
                        if (proto != null && addr != null && cmd != null) {
                            result.add(
                                IrButton(
                                    id = nextId++,
                                    name = name,
                                    role = inferRole(name),
                                    signal = IrSignal.Parsed(proto, addr, cmd),
                                ),
                            )
                        }
                    }
                    "raw" -> {
                        val freq = cur["frequency"]?.toDoubleOrNull()?.roundToInt() ?: 38000
                        val data = cur["data"] ?: ""
                        val raw = parseRawPattern(freq, data, valuesAreCycles = false)
                        if (raw != null && raw.patternUs.size >= 4) {
                            result.add(
                                IrButton(
                                    id = nextId++,
                                    name = name,
                                    role = inferRole(name),
                                    signal = raw,
                                ),
                            )
                        }
                    }
                }
            }
            cur.clear()
        }

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                flush()
                continue
            }
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                if (key == "name") {
                    flush()
                }
                if (key in setOf("name", "type", "protocol", "address", "command", "frequency", "data")) {
                    cur[key] = value
                }
            }
        }
        flush()
        return result
    }

    fun inferRole(name: String): ButtonRole {
        val s = name.lowercase()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9+_-]"), "")
            .replace(Regex("[_-]+"), "_")
            .trim('_')

        return when (s) {
            "power", "pwr", "pow", "on_off", "power_toggle", "standby" -> ButtonRole.POWER
            "power_on", "pwr_on", "on", "turn_on" -> ButtonRole.POWER_ON
            "power_off", "pwr_off", "off", "turn_off" -> ButtonRole.POWER_OFF
            "mute", "audio_mute", "vol_mute" -> ButtonRole.MUTE
            "vol_up", "vol+", "volume_up", "volume+", "vol_+", "volup" -> ButtonRole.VOL_UP
            "vol_dn", "vol_down", "vol-", "volume_down", "volume-", "vol_-", "voldn", "voldown" -> ButtonRole.VOL_DOWN
            "ch_next", "ch_up", "ch+", "channel_up", "channel+", "prog_up", "chup" -> ButtonRole.CH_UP
            "ch_prev", "ch_dn", "ch_down", "ch-", "channel_down", "channel-", "prog_dn", "chdn" -> ButtonRole.CH_DOWN
            "up", "dir_up", "nav_up" -> ButtonRole.UP
            "down", "dn", "dir_down", "nav_down" -> ButtonRole.DOWN
            "left", "dir_left", "nav_left" -> ButtonRole.LEFT
            "right", "dir_right", "nav_right" -> ButtonRole.RIGHT
            "ok", "enter", "select", "center" -> ButtonRole.OK
            "back", "return", "ret" -> ButtonRole.BACK
            "home", "smart", "smart_hub" -> ButtonRole.HOME
            "menu", "settings", "setup", "options" -> ButtonRole.MENU
            "exit", "quit" -> ButtonRole.EXIT
            "info", "display" -> ButtonRole.INFO
            "source", "input", "av", "hdmi", "aux" -> ButtonRole.INPUT
            "guide", "epg" -> ButtonRole.GUIDE
            "last", "prev_ch", "recall", "last_ch" -> ButtonRole.LAST_CH
            "0", "num_0", "digit_0" -> ButtonRole.NUM_0
            "1", "num_1", "digit_1" -> ButtonRole.NUM_1
            "2", "num_2", "digit_2" -> ButtonRole.NUM_2
            "3", "num_3", "digit_3" -> ButtonRole.NUM_3
            "4", "num_4", "digit_4" -> ButtonRole.NUM_4
            "5", "num_5", "digit_5" -> ButtonRole.NUM_5
            "6", "num_6", "digit_6" -> ButtonRole.NUM_6
            "7", "num_7", "digit_7" -> ButtonRole.NUM_7
            "8", "num_8", "digit_8" -> ButtonRole.NUM_8
            "9", "num_9", "digit_9" -> ButtonRole.NUM_9
            "play", "play_pause" -> ButtonRole.PLAY
            "pause" -> ButtonRole.PAUSE
            "stop" -> ButtonRole.STOP
            "rew", "rewind", "fb" -> ButtonRole.REW
            "ff", "fast_forward", "fwd" -> ButtonRole.FF
            "prev", "previous", "skip_back" -> ButtonRole.PREV
            "next", "skip_forward" -> ButtonRole.NEXT
            "rec", "record" -> ButtonRole.REC
            "eject", "open", "open_close" -> ButtonRole.EJECT
            "red", "r" -> ButtonRole.COLOR_RED
            "green", "g" -> ButtonRole.COLOR_GREEN
            "yellow", "y" -> ButtonRole.COLOR_YELLOW
            "blue", "b" -> ButtonRole.COLOR_BLUE
            "white", "w" -> ButtonRole.COLOR_WHITE
            "bright+", "bright_up", "brightness+" -> ButtonRole.BRIGHT_UP
            "bright-", "bright_dn", "brightness-" -> ButtonRole.BRIGHT_DOWN
            "temp+", "temp_up", "temperature+" -> ButtonRole.TEMP_UP
            "temp-", "temp_dn", "temp_down", "temperature-" -> ButtonRole.TEMP_DOWN
            "mode", "ac_mode" -> ButtonRole.MODE
            "fan", "fan_speed", "speed" -> ButtonRole.FAN_SPEED
            "swing", "osc", "oscillate" -> ButtonRole.SWING
            "timer", "sleep" -> ButtonRole.TIMER
            "cool" -> ButtonRole.AC_COOL
            "heat" -> ButtonRole.AC_HEAT
            "dry" -> ButtonRole.AC_DRY
            "auto" -> ButtonRole.AC_AUTO
            "shutter", "shoot", "s" -> ButtonRole.SHUTTER
            "shutter_2s", "2s" -> ButtonRole.SHUTTER_2S
            else -> ButtonRole.OTHER
        }
    }
}
