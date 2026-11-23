package com.openjarvis.android.core.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.PowerManager
import com.openjarvis.android.core.domain.model.DeviceTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DeviceTelemetryProvider {
    val telemetryFlow: StateFlow<DeviceTelemetry>
    fun snapshot(): DeviceTelemetry
}

@Singleton
class DeviceStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceTelemetryProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _telemetry = MutableStateFlow(readCurrentTelemetry())
    override val telemetryFlow: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    override fun snapshot(): DeviceTelemetry {
        val current = readCurrentTelemetry()
        _telemetry.value = current
        return current
    }

    private fun refresh() {
        _telemetry.value = readCurrentTelemetry()
    }

    private fun readCurrentTelemetry(): DeviceTelemetry {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isUnmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availableRamMb = memInfo.availMem / (1024L * 1024L)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)

        val thermalStatus = powerManager.currentThermalStatus
        val isThermalThrottled = thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE

        return DeviceTelemetry(
            isOnline = isOnline,
            isUnmetered = isUnmetered,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            isPowerSaveMode = powerManager.isPowerSaveMode,
            isThermalThrottled = isThermalThrottled,
            availableRamMb = availableRamMb,
            totalRamMb = totalRamMb,
        )
    }
}
