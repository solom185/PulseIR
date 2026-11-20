package com.pulseir.remote.data

import com.pulseir.remote.ir.AcState
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrSignal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class CatalogCategory(
    val id: String,
    val displayEn: String,
    val displayAr: String,
    val sortOrder: Int,
    val remoteCount: Int,
    val brandCount: Int,
) {
    fun localizedName(isArabic: Boolean): String = if (isArabic) displayAr else displayEn
}

data class CatalogBrand(
    val name: String,
    val remoteCount: Int,
)

data class CatalogRemote(
    val id: Long,
    val categoryId: String,
    val brand: String,
    val model: String,
    val buttonCount: Int,
    val hasPower: Boolean,
    val powerFingerprint: String?,
)

data class ScanCandidate(
    val fingerprint: String,
    val sampleRemoteId: Long,
    val sampleBrand: String,
    val sampleModel: String,
    val matchCount: Int,
    val powerButton: IrButton,
)

@Serializable
data class SavedDevice(
    val id: String,
    val name: String,
    val room: String = "living",
    val categoryId: String,
    val brand: String,
    val model: String,
    val catalogRemoteId: Long? = null,
    val smartAcProtocolId: String? = null,
    val acState: AcState? = null,
    val customButtons: List<IrButton> = emptyList(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
sealed interface CustomAction {
    @Serializable
    @SerialName("signal")
    data class DirectSignal(
        val sourceDeviceName: String = "",
        val signal: IrSignal,
    ) : CustomAction

    @Serializable
    @SerialName("macro")
    data class MacroRef(val macroId: String) : CustomAction
}

@Serializable
data class CustomRemoteButton(
    val id: String,
    val label: String,
    val colorHex: Long = 0xFF252D3D,
    val isWide: Boolean = false,
    val action: CustomAction,
)

@Serializable
data class CustomRemoteTab(
    val id: String,
    val title: String,
    val buttons: List<CustomRemoteButton> = emptyList(),
)

@Serializable
data class CustomRemote(
    val id: String,
    val name: String,
    val tabs: List<CustomRemoteTab> = listOf(CustomRemoteTab(id = "tab_1", title = "Main")),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
sealed interface MacroStep {
    @Serializable
    @SerialName("send_ir")
    data class SendIr(
        val id: String,
        val label: String,
        val deviceName: String,
        val signal: IrSignal,
    ) : MacroStep

    @Serializable
    @SerialName("delay")
    data class Delay(
        val id: String,
        val delayMs: Long = 1000L,
    ) : MacroStep
}

@Serializable
data class Macro(
    val id: String,
    val name: String,
    val colorHex: Long = 0xFF00B4D8,
    val steps: List<MacroStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class CustomIrCode(
    val id: String,
    val name: String,
    val signal: IrSignal,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class UserStoreData(
    val version: Int = 1,
    val devices: List<SavedDevice> = emptyList(),
    val customRemotes: List<CustomRemote> = emptyList(),
    val macros: List<Macro> = emptyList(),
    val customCodes: List<CustomIrCode> = emptyList(),
)

@Serializable
data class PulseBackup(
    val app: String = "PulseIR",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val data: UserStoreData,
)
