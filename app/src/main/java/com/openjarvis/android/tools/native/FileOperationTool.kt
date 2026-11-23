package com.openjarvis.android.tools.native

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.openjarvis.android.core.config.JarvisPreferencesRepository
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

class FileReadTool @Inject constructor(
    private val preferencesRepository: JarvisPreferencesRepository,
) : AndroidTool {
    override val spec = ToolSpec(
        name = "read_workspace_files",
        description = "List or read text files inside the user-authorized Storage Access Framework folder or app workspace.",
        riskLevel = ToolRiskLevel.SAFE_READ,
        parameters = listOf(
            ToolParamSchema(
                name = "action",
                type = "string",
                description = "Operation: 'list' or 'read'.",
                required = true,
                enumValues = listOf("list", "read"),
            ),
            ToolParamSchema(
                name = "file_name",
                type = "string",
                description = "File name to read when action is 'read'.",
                required = false,
            ),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val action = args.str("action")?.lowercase() ?: "list"
        val fileName = args.str("file_name")
        val safUriStr = preferencesRepository.settingsFlow.first().safTreeUri

        if (!safUriStr.isNullOrBlank()) {
            val treeUri = Uri.parse(safUriStr)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc != null && rootDoc.canRead()) {
                return when (action) {
                    "read" -> {
                        if (fileName == null) {
                            return ToolExecutionResult(spec.name, false, "Missing 'file_name'.")
                        }
                        val doc = rootDoc.findFile(fileName)
                            ?: return ToolExecutionResult(
                                spec.name,
                                false,
                                "File '$fileName' not found in SAF workspace.",
                            )
                        val text = context.contentResolver.openInputStream(doc.uri)
                            ?.bufferedReader()
                            ?.use { it.readText().take(8000) }
                            ?: return ToolExecutionResult(spec.name, false, "Cannot read '$fileName'.")
                        ToolExecutionResult(
                            toolName = spec.name,
                            success = true,
                            summary = "Read '${doc.name}' (${text.length} chars):\n$text",
                            verified = true,
                        )
                    }
                    else -> {
                        val names = rootDoc.listFiles()
                            .take(30)
                            .joinToString(", ") { "${it.name}${if (it.isDirectory) "/" else ""}" }
                        ToolExecutionResult(
                            toolName = spec.name,
                            success = true,
                            summary = "SAF folder files: ${names.ifBlank { "(empty)" }}",
                            verified = true,
                        )
                    }
                }
            }
        }

        val workspaceDir = File(context.filesDir, "workspace").apply { if (!exists()) mkdirs() }
        return when (action) {
            "read" -> {
                val safeName = File(fileName ?: "").name
                val target = File(workspaceDir, safeName)
                if (!target.exists() || !target.isFile) {
                    ToolExecutionResult(
                        toolName = spec.name,
                        success = false,
                        summary = "File '$safeName' not found in app workspace (no SAF folder linked yet).",
                    )
                } else {
                    val content = target.readText().take(8000)
                    ToolExecutionResult(
                        toolName = spec.name,
                        success = true,
                        summary = "Read '$safeName' from app workspace:\n$content",
                        verified = true,
                    )
                }
            }
            else -> {
                val files = workspaceDir.listFiles()?.joinToString(", ") { it.name }.orEmpty()
                ToolExecutionResult(
                    toolName = spec.name,
                    success = true,
                    summary = "App workspace files (link a shared folder in Settings > SAF Workspace): ${files.ifBlank { "(empty)" }}",
                    verified = true,
                )
            }
        }
    }
}

class FileWriteTool @Inject constructor(
    private val preferencesRepository: JarvisPreferencesRepository,
) : AndroidTool {
    override val spec = ToolSpec(
        name = "write_workspace_file",
        description = "Create or overwrite a text file inside the user-authorized SAF folder or app workspace. Requires user confirmation.",
        riskLevel = ToolRiskLevel.CONFIRMATION_REQUIRED,
        parameters = listOf(
            ToolParamSchema("file_name", "string", "Target file name (e.g. notes.txt).", required = true),
            ToolParamSchema("content", "string", "Text content to write.", required = true),
        ),
    )

    override fun describeConfirmation(args: JsonObject): String {
        val name = args.str("file_name") ?: "(unknown)"
        val len = args.str("content")?.length ?: 0
        return "Write $len characters to workspace file '$name'"
    }

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val rawName = args.str("file_name")
            ?: return ToolExecutionResult(spec.name, false, "Missing 'file_name'.")
        val safeName = File(rawName).name
        val content = args.str("content") ?: ""
        val safUriStr = preferencesRepository.settingsFlow.first().safTreeUri

        if (!safUriStr.isNullOrBlank()) {
            val treeUri = Uri.parse(safUriStr)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc != null && rootDoc.canWrite()) {
                val existing = rootDoc.findFile(safeName)
                val target = existing ?: rootDoc.createFile("text/plain", safeName)
                    ?: return ToolExecutionResult(spec.name, false, "Could not create '$safeName' in SAF folder.")
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: return ToolExecutionResult(spec.name, false, "Failed to open output stream for '$safeName'.")
                return ToolExecutionResult(
                    toolName = spec.name,
                    success = true,
                    summary = "Saved '$safeName' (${content.length} chars) to SAF workspace.",
                    verified = rootDoc.findFile(safeName)?.exists() == true,
                )
            }
        }

        val workspaceDir = File(context.filesDir, "workspace").apply { if (!exists()) mkdirs() }
        val target = File(workspaceDir, safeName)
        target.writeText(content)
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Saved '$safeName' (${content.length} chars) to app workspace.",
            verified = target.exists() && target.length() >= 0,
        )
    }
}
