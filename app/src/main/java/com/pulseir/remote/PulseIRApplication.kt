package com.pulseir.remote

import android.app.Application
import com.pulseir.remote.data.AppPreferences
import com.pulseir.remote.data.CatalogRepository
import com.pulseir.remote.data.UserRepository
import com.pulseir.remote.ir.IrTransmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PulseIRApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var preferences: AppPreferences
        private set
    lateinit var catalogRepository: CatalogRepository
        private set
    lateinit var userRepository: UserRepository
        private set
    lateinit var irTransmitter: IrTransmitter
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        catalogRepository = CatalogRepository(this)
        userRepository = UserRepository(this, catalogRepository)
        irTransmitter = IrTransmitter(this).apply {
            hapticLevel = preferences.settings.value.hapticLevel
        }
        // Pre-extract offline SQLite catalog in background so catalog browsing is instant
        appScope.launch {
            runCatching { catalogRepository.warmup() }
        }
    }
}
