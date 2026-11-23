package com.openjarvis.android.tools.native

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

class OpenAppTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "open_app",
        description = "Launch an installed Android application by app name or package name.",
        riskLevel = ToolRiskLevel.DEVICE_STATE,
        parameters = listOf(
            ToolParamSchema(
                name = "app_name",
                type = "string",
                description = "Human-readable app name (e.g. YouTube, Settings, Chrome) or package name.",
                required = true,
            ),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val query = args.str("app_name")
            ?: return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                summary = "Missing required 'app_name' parameter.",
                recoverable = true,
            )

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val resolveFlags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            pm.queryIntentActivities(mainIntent, resolveFlags)
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val normalizedQuery = query.lowercase()
        val match = candidates.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            val pkg = info.activityInfo.packageName.lowercase()
            label == normalizedQuery || pkg == normalizedQuery
        } ?: candidates.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            val pkg = info.activityInfo.packageName.lowercase()
            label.contains(normalizedQuery) || pkg.contains(normalizedQuery)
        }

        if (match == null) {
            val sampleApps = candidates.take(8).joinToString(", ") { it.loadLabel(pm).toString() }
            return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                summary = "No installed launcher app matched '$query'. Available examples: $sampleApps",
                recoverable = true,
            )
        }

        val targetPackage = match.activityInfo.packageName
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return ToolExecutionResult(
            toolName = spec.name,
            success = false,
            summary = "App '$targetPackage' has no launchable intent.",
            recoverable = false,
        )

        context.startActivity(launchIntent)
        val appLabel = match.loadLabel(pm).toString()
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Launched $appLabel ($targetPackage).",
            verified = true,
        )
    }
}
