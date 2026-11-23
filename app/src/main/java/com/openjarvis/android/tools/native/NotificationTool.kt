package com.openjarvis.android.tools.native

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openjarvis.android.R
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

class NotificationTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "post_notification",
        description = "Post a local Android notification with a title and message.",
        riskLevel = ToolRiskLevel.DEVICE_STATE,
        requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        },
        parameters = listOf(
            ToolParamSchema("title", "string", "Notification title.", required = true),
            ToolParamSchema("message", "string", "Notification body text.", required = true),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val title = args.str("title") ?: "Open JARVIS"
        val message = args.str("message")
            ?: return ToolExecutionResult(spec.name, false, "Missing 'message' parameter.")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.jarvis_alerts_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.jarvis_alerts_channel_description)
        }
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        val id = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        NotificationManagerCompat.from(context).notify(id, notification)
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Posted notification '$title'.",
            verified = true,
        )
    }

    companion object {
        const val CHANNEL_ID = "jarvis_alerts_channel"
    }
}
