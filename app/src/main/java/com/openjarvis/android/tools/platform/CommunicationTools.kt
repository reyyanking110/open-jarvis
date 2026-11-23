package com.openjarvis.android.tools.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

class DialOrCallTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "call_phone",
        description = "Initiate a phone call or open the Android dialer for a phone number. Requires user confirmation.",
        riskLevel = ToolRiskLevel.CONFIRMATION_REQUIRED,
        parameters = listOf(
            ToolParamSchema(
                name = "phone_number",
                type = "string",
                description = "Phone number to dial or call.",
                required = true,
            ),
            ToolParamSchema(
                name = "direct_call",
                type = "boolean",
                description = "If true and CALL_PHONE permission is granted, places the call directly; otherwise opens the dialer.",
                required = false,
            ),
        ),
    )

    override fun describeConfirmation(args: JsonObject): String {
        val number = args.str("phone_number") ?: "(unknown)"
        val direct = args.bool("direct_call") == true
        return if (direct) "Place a direct phone call to $number" else "Open the phone dialer with $number"
    }

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val number = args.str("phone_number")
            ?: return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                summary = "Missing 'phone_number' parameter.",
            )
        val wantDirect = args.bool("direct_call") == true
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (wantDirect && hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                summary = "No phone/dialer app available on this device.",
                recoverable = false,
            )
        }
        context.startActivity(intent)
        val modeNote = if (action == Intent.ACTION_CALL) {
            "Started direct call to $number."
        } else if (wantDirect) {
            "CALL_PHONE permission not granted; opened dialer with $number instead."
        } else {
            "Opened dialer with $number."
        }
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = modeNote,
            verified = true,
        )
    }
}

class SendMessageIntentTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "compose_message",
        description = "Open SMS or Email app pre-filled with recipient and message body. Requires user confirmation.",
        riskLevel = ToolRiskLevel.CONFIRMATION_REQUIRED,
        parameters = listOf(
            ToolParamSchema(
                name = "channel",
                type = "string",
                description = "Messaging channel: sms or email.",
                required = true,
                enumValues = listOf("sms", "email"),
            ),
            ToolParamSchema(
                name = "recipient",
                type = "string",
                description = "Phone number for SMS or email address for Email.",
                required = true,
            ),
            ToolParamSchema(
                name = "body",
                type = "string",
                description = "Message body text.",
                required = true,
            ),
            ToolParamSchema(
                name = "subject",
                type = "string",
                description = "Optional email subject.",
                required = false,
            ),
        ),
    )

    override fun describeConfirmation(args: JsonObject): String {
        val channel = args.str("channel") ?: "sms"
        val recipient = args.str("recipient") ?: "(unknown)"
        val body = args.str("body") ?: ""
        return "Compose ${channel.uppercase()} to $recipient: \"${body.take(80)}\""
    }

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val channel = args.str("channel")?.lowercase() ?: "sms"
        val recipient = args.str("recipient")
            ?: return ToolExecutionResult(spec.name, false, "Missing 'recipient' parameter.")
        val body = args.str("body") ?: ""
        val subject = args.str("subject") ?: "Message from Open JARVIS"

        val intent = when (channel) {
            "email" -> Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${Uri.encode(recipient)}")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            else -> Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(recipient)}")
                putExtra("sms_body", body)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                summary = "No $channel app installed to handle SENDTO intent.",
                recoverable = false,
            )
        }
        context.startActivity(intent)
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Opened $channel composer for $recipient.",
            verified = true,
        )
    }
}
