package com.pulseir.remote.data

import com.pulseir.remote.ir.AcFanSpeed
import com.pulseir.remote.ir.AcMode
import com.pulseir.remote.ir.AcState
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrProtocol
import com.pulseir.remote.ir.IrSignal
import com.pulseir.remote.ir.SmartAcProtocol
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun pulseBackup_roundTripsDevicesAcStateCustomRemotesMacrosAndCodes() {
        val acDevice = SavedDevice(
            id = "dev-ac-1",
            name = "Bedroom AC",
            room = "bedroom",
            categoryId = "ac",
            brand = "Sharp",
            model = "Smart AC",
            smartAcProtocolId = SmartAcProtocol.SHARP.id,
            acState = AcState(
                powerOn = true,
                mode = AcMode.COOL,
                tempC = 21,
                fanSpeed = AcFanSpeed.HIGH,
                swing = true,
            ),
            lastUsedAt = 1_700_000_000_000L,
            createdAt = 1_700_000_000_000L,
        )

        val customDevice = SavedDevice(
            id = "dev-tv-1",
            name = "Living TV",
            room = "living",
            categoryId = "tv",
            brand = "Samsung",
            model = "UN55",
            catalogRemoteId = 42L,
            customButtons = listOf(
                IrButton(1L, "Power", ButtonRole.POWER, IrSignal.Parsed(IrProtocol.Samsung32, 0x07, 0x02)),
            ),
            lastUsedAt = 1_700_000_000_000L,
            createdAt = 1_700_000_000_000L,
        )

        val macro = Macro(
            id = "macro-1",
            name = "Movie Night",
            steps = listOf(
                MacroStep.SendIr("s1", "TV Power", "Living TV", IrSignal.Parsed(IrProtocol.Samsung32, 0x07, 0x02)),
                MacroStep.Delay("s2", 1500L),
            ),
            createdAt = 1_700_000_000_000L,
        )

        val customRemote = CustomRemote(
            id = "cr-1",
            name = "Unified",
            tabs = listOf(
                CustomRemoteTab(
                    id = "t1",
                    title = "Main",
                    buttons = listOf(
                        CustomRemoteButton("b1", "Movie", action = CustomAction.MacroRef("macro-1")),
                        CustomRemoteButton(
                            "b2",
                            "Raw",
                            action = CustomAction.DirectSignal("AC", IrSignal.Raw(38000, listOf(9000, 4500, 560))),
                        ),
                    ),
                ),
            ),
            createdAt = 1_700_000_000_000L,
        )

        val customCode = CustomIrCode(
            id = "code-1",
            name = "Secret Menu",
            signal = IrSignal.Parsed(IrProtocol.NEC, 0x04, 0xFF),
            createdAt = 1_700_000_000_000L,
        )

        val original = PulseBackup(
            exportedAt = 1_700_000_000_000L,
            data = UserStoreData(
                devices = listOf(acDevice, customDevice),
                customRemotes = listOf(customRemote),
                macros = listOf(macro),
                customCodes = listOf(customCode),
            ),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PulseBackup>(encoded)

        assertEquals(original, decoded)
        assertEquals(21, decoded.data.devices[0].acState?.tempC)
        assertEquals(AcFanSpeed.HIGH, decoded.data.devices[0].acState?.fanSpeed)
        assertNull(decoded.data.devices[1].acState)
    }

    @Test
    fun pulseBackup_deserializesV100BackupWithoutAcStateField() {
        val legacyV100Json = """
            {
              "app": "PulseIR",
              "version": 1,
              "exportedAt": 1700000000000,
              "data": {
                "version": 1,
                "devices": [
                  {
                    "id": "legacy-1",
                    "name": "Carrier AC",
                    "room": "living",
                    "categoryId": "ac",
                    "brand": "Carrier",
                    "model": "Coolix",
                    "smartAcProtocolId": "smart_ac_coolix",
                    "lastUsedAt": 1700000000000,
                    "createdAt": 1700000000000
                  }
                ]
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<PulseBackup>(legacyV100Json)
        assertEquals(1, decoded.data.devices.size)
        assertEquals("Carrier AC", decoded.data.devices[0].name)
        assertEquals("smart_ac_coolix", decoded.data.devices[0].smartAcProtocolId)
        assertNull(decoded.data.devices[0].acState)
    }
}
