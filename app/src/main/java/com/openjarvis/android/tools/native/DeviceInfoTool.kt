package com.openjarvis.android.tools.native

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeviceInfoTool @Inject constructor(
    private val telemetryProvider: DeviceTelemetryProvider,
) : AndroidTool {
    override val spec = ToolSpec(
        name = "get_device_info",
        description = "Read live battery level, charging state, RAM, internal storage, network, thermal status, and Android OS details.",
        riskLevel = ToolRiskLevel.SAFE_READ,
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val t = telemetryProvider.snapshot()
        val stat = StatFs(Environment.getDataDirectory().path)
        val availStorageGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
        val totalStorageGb = stat.totalBytes / (1024.0 * 1024.0 * 1024.0)

        val json = buildJsonObject {
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android_version", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            put("battery_percent", t.batteryPercent)
            put("charging", t.isCharging)
            put("power_save_mode", t.isPowerSaveMode)
            put("thermal_throttled", t.isThermalThrottled)
            put("online", t.isOnline)
            put("unmetered_network", t.isUnmetered)
            put("available_ram_mb", t.availableRamMb)
            put("total_ram_mb", t.totalRamMb)
            put("available_storage_gb", "%.1f".format(availStorageGb))
            put("total_storage_gb", "%.1f".format(totalStorageGb))
        }

        val summary = buildString {
            append("${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}): ")
            append("Battery ${t.batteryPercent}% (${if (t.isCharging) "charging" else "discharging"}), ")
            append("RAM ${t.availableRamMb}/${t.totalRamMb} MB free, ")
            append("Storage ${"%.1f".format(availStorageGb)}/${"%.1f".format(totalStorageGb)} GB free, ")
            append("Network ${if (t.isOnline) "online" else "offline"}")
            if (t.isThermalThrottled) append(", THERMAL THROTTLED")
        }

        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = summary,
            dataJson = json.toString(),
            verified = true,
        )
    }
}
