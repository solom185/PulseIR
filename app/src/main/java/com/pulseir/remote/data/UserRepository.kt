package com.pulseir.remote.data

import android.content.Context
import com.pulseir.remote.ir.AcState
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.lockscreen.RemoteNotificationController
import com.pulseir.remote.widget.QuickRemoteWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * 100% Offline local user data repository for saved devices, custom remotes,
 * macros, custom IR codes, and complete JSON backup/restore.
 */
class UserRepository(
    context: Context,
    private val catalogRepository: CatalogRepository,
) {
    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "pulseir_user_data.json")
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PulseIR-UserStore").apply { isDaemon = true }
    }
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(loadFromDisk())
    val state: StateFlow<UserStoreData> = _state.asStateFlow()

    @Synchronized
    private fun updateAndPersist(transform: (UserStoreData) -> UserStoreData) {
        val next = transform(_state.value)
        _state.value = next
        // Persist atomically off the Main Thread while preserving strict write order
        ioExecutor.execute { saveToDisk(next) }
    }

    private fun loadFromDisk(): UserStoreData {
        if (!storeFile.exists()) return UserStoreData()
        return runCatching {
            json.decodeFromString<UserStoreData>(storeFile.readText(Charsets.UTF_8))
        }.getOrDefault(UserStoreData())
    }

    private fun saveToDisk(data: UserStoreData) {
        runCatching {
            val tmp = File(storeFile.parentFile, "pulseir_user_data.json.tmp")
            tmp.writeText(json.encodeToString(data), Charsets.UTF_8)
            if (storeFile.exists()) storeFile.delete()
            tmp.renameTo(storeFile)
        }
    }

    // --- Saved Devices ---
    fun addDevice(
        name: String,
        room: String,
        categoryId: String,
        brand: String,
        model: String,
        catalogRemoteId: Long? = null,
        smartAcProtocolId: String? = null,
        customButtons: List<IrButton> = emptyList(),
    ): SavedDevice {
        val device = SavedDevice(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "$brand $model".trim() },
            room = room,
            categoryId = categoryId,
            brand = brand,
            model = model,
            catalogRemoteId = catalogRemoteId,
            smartAcProtocolId = smartAcProtocolId,
            acState = if (smartAcProtocolId != null) AcState() else null,
            customButtons = customButtons,
        )
        updateAndPersist { it.copy(devices = listOf(device) + it.devices) }
        return device
    }

    fun markDeviceUsed(deviceId: String) {
        updateAndPersist { data ->
            data.copy(
                devices = data.devices.map { d ->
                    if (d.id == deviceId) d.copy(lastUsedAt = System.currentTimeMillis()) else d
                },
            )
        }
    }

    fun updateDeviceAcState(deviceId: String, acState: AcState) {
        updateAndPersist { data ->
            data.copy(
                devices = data.devices.map { d ->
                    if (d.id == deviceId) {
                        d.copy(acState = acState, lastUsedAt = System.currentTimeMillis())
                    } else d
                },
            )
        }
    }

    fun renameDevice(deviceId: String, newName: String, newRoom: String) {
        updateAndPersist { data ->
            data.copy(
                devices = data.devices.map { d ->
                    if (d.id == deviceId) d.copy(name = newName.trim().ifEmpty { d.name }, room = newRoom) else d
                },
            )
        }
    }

    fun deleteDevice(deviceId: String) {
        updateAndPersist { data ->
            data.copy(devices = data.devices.filterNot { it.id == deviceId })
        }
        if (RemoteNotificationController.pinnedDeviceId.value == deviceId) {
            RemoteNotificationController.dismiss(appContext)
        }
        QuickRemoteWidgetProvider.refreshAllForDevice(appContext, deviceId)
    }

    fun getDevice(deviceId: String): SavedDevice? = _state.value.devices.firstOrNull { it.id == deviceId }

    fun getDeviceButtonsBlocking(device: SavedDevice): List<IrButton> {
        val smartProto = com.pulseir.remote.ir.SmartAcProtocol.fromId(device.smartAcProtocolId)
        if (smartProto != null) {
            val base = device.acState ?: AcState()
            val onState = base.copy(
                powerOn = true,
                tempC = base.tempC.coerceIn(smartProto.minTempC, smartProto.maxTempC),
            )
            return listOf(
                IrButton(
                    id = 1L,
                    name = "${onState.mode.name} ${onState.tempC}°C ON",
                    role = com.pulseir.remote.ir.ButtonRole.POWER_ON,
                    signal = com.pulseir.remote.ir.AcProtocolEngine.encode(smartProto, onState),
                ),
                IrButton(
                    id = 2L,
                    name = "OFF",
                    role = com.pulseir.remote.ir.ButtonRole.POWER_OFF,
                    signal = com.pulseir.remote.ir.AcProtocolEngine.encode(
                        smartProto,
                        base.copy(powerOn = false),
                    ),
                ),
            )
        }
        return if (device.customButtons.isNotEmpty()) {
            device.customButtons
        } else if (device.catalogRemoteId != null) {
            catalogRepository.getButtonsBlocking(device.catalogRemoteId)
        } else {
            emptyList()
        }
    }

    suspend fun getDeviceButtons(device: SavedDevice): List<IrButton> {
        val smartProto = com.pulseir.remote.ir.SmartAcProtocol.fromId(device.smartAcProtocolId)
        if (smartProto != null) return getDeviceButtonsBlocking(device)
        return if (device.customButtons.isNotEmpty()) {
            device.customButtons
        } else if (device.catalogRemoteId != null) {
            catalogRepository.getButtons(device.catalogRemoteId)
        } else {
            emptyList()
        }
    }

    // --- Custom Remotes ---
    fun createCustomRemote(name: String): CustomRemote {
        val remote = CustomRemote(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "My Remote" },
            tabs = listOf(CustomRemoteTab(id = UUID.randomUUID().toString(), title = "Main")),
        )
        updateAndPersist { it.copy(customRemotes = listOf(remote) + it.customRemotes) }
        return remote
    }

    fun updateCustomRemote(remote: CustomRemote) {
        updateAndPersist { data ->
            data.copy(customRemotes = data.customRemotes.map { if (it.id == remote.id) remote else it })
        }
    }

    fun deleteCustomRemote(remoteId: String) {
        updateAndPersist { data ->
            data.copy(customRemotes = data.customRemotes.filterNot { it.id == remoteId })
        }
    }

    // --- Macros ---
    fun saveMacro(macro: Macro) {
        updateAndPersist { data ->
            val exists = data.macros.any { it.id == macro.id }
            val list = if (exists) {
                data.macros.map { if (it.id == macro.id) macro else it }
            } else {
                listOf(macro) + data.macros
            }
            data.copy(macros = list)
        }
    }

    fun deleteMacro(macroId: String) {
        updateAndPersist { data ->
            data.copy(macros = data.macros.filterNot { it.id == macroId })
        }
    }

    fun getMacro(macroId: String): Macro? = _state.value.macros.firstOrNull { it.id == macroId }

    // --- Custom IR Codes ---
    fun saveCustomCode(code: CustomIrCode) {
        updateAndPersist { data ->
            val exists = data.customCodes.any { it.id == code.id }
            val list = if (exists) {
                data.customCodes.map { if (it.id == code.id) code else it }
            } else {
                listOf(code) + data.customCodes
            }
            data.copy(customCodes = list)
        }
    }

    fun deleteCustomCode(codeId: String) {
        updateAndPersist { data ->
            data.copy(customCodes = data.customCodes.filterNot { it.id == codeId })
        }
    }

    // --- Full Offline Backup & Restore ---
    fun exportBackupJson(): String {
        val backup = PulseBackup(data = _state.value)
        return json.encodeToString(backup)
    }

    fun importBackupJson(rawJson: String): Boolean {
        return runCatching {
            val parsed = json.decodeFromString<PulseBackup>(rawJson)
            updateAndPersist { parsed.data }
            true
        }.getOrElse {
            // Also accept raw UserStoreData format gracefully
            runCatching {
                val parsed = json.decodeFromString<UserStoreData>(rawJson)
                updateAndPersist { parsed }
                true
            }.getOrDefault(false)
        }
    }
}
