package com.openjarvis.android.tools.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

class SettingsShortcutTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "open_settings",
        description = "Open an Android Settings screen or quick panel (wifi, internet, bluetooth, volume, display, battery, notifications, app_details).",
        riskLevel = ToolRiskLevel.DEVICE_STATE,
        parameters = listOf(
            ToolParamSchema(
                name = "section",
                type = "string",
                description = "Settings section to open.",
                required = true,
                enumValues = listOf(
                    "wifi",
                    "internet",
                    "bluetooth",
                    "volume",
                    "display",
                    "battery",
                    "notifications",
                    "app_details",
                    "general",
                ),
            ),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val section = args.str("section")?.lowercase() ?: "general"
        val intent = when (section) {
            "wifi" -> Intent(Settings.Panel.ACTION_WIFI)
            "internet" -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            "volume" -> Intent(Settings.Panel.ACTION_VOLUME)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            "app_details" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) == null) {
            val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            return ToolExecutionResult(
                toolName = spec.name,
                success = true,
                summary = "Section '$section' unavailable; opened main Android Settings.",
                verified = true,
            )
        }
        context.startActivity(intent)
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Opened Android '$section' settings.",
            verified = true,
        )
    }
}
