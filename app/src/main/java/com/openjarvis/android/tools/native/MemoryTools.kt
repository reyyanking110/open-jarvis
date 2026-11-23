package com.openjarvis.android.tools.native

import android.content.Context
import com.openjarvis.android.core.domain.model.MemoryCategory
import com.openjarvis.android.data.repository.MemoryRepository
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

class SearchMemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository,
) : AndroidTool {
    override val spec = ToolSpec(
        name = "search_memory",
        description = "Search persistent user-approved memories stored in on-device Room storage.",
        riskLevel = ToolRiskLevel.SAFE_READ,
        parameters = listOf(
            ToolParamSchema("query", "string", "Keywords or topic to look up in memory.", required = true),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val query = args.str("query")
            ?: return ToolExecutionResult(spec.name, false, "Missing 'query' parameter.")
        val hits = memoryRepository.retrieveRelevantMemories(query, limit = 5)
        if (hits.isEmpty()) {
            return ToolExecutionResult(
                toolName = spec.name,
                success = true,
                summary = "No saved memories matched '$query'.",
                verified = true,
            )
        }
        val formatted = hits.joinToString("\n") { "- [${it.category.label}] ${it.title}: ${it.content}" }
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Found ${hits.size} memory entries:\n$formatted",
            verified = true,
        )
    }
}

class SaveMemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository,
) : AndroidTool {
    override val spec = ToolSpec(
        name = "save_memory",
        description = "Save a personal fact, preference, or routine to persistent memory. Requires user approval.",
        riskLevel = ToolRiskLevel.CONFIRMATION_REQUIRED,
        parameters = listOf(
            ToolParamSchema("title", "string", "Short memory title.", required = true),
            ToolParamSchema("content", "string", "Detailed fact or preference to remember.", required = true),
            ToolParamSchema(
                name = "category",
                type = "string",
                description = "Category: preference, fact, routine, or device_note.",
                required = false,
                enumValues = listOf("preference", "fact", "routine", "device_note"),
            ),
            ToolParamSchema("tags", "string", "Comma-separated keywords.", required = false),
        ),
    )

    override fun describeConfirmation(args: JsonObject): String {
        val title = args.str("title") ?: "Memory"
        val content = args.str("content") ?: ""
        return "Save to persistent memory — \"$title\": ${content.take(100)}"
    }

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val title = args.str("title")
            ?: return ToolExecutionResult(spec.name, false, "Missing 'title' parameter.")
        val content = args.str("content")
            ?: return ToolExecutionResult(spec.name, false, "Missing 'content' parameter.")
        val cat = when (args.str("category")?.lowercase()) {
            "preference" -> MemoryCategory.PREFERENCE
            "routine" -> MemoryCategory.ROUTINE
            "device_note" -> MemoryCategory.DEVICE_NOTE
            else -> MemoryCategory.FACT
        }
        val tags = args.str("tags") ?: ""
        val id = memoryRepository.saveApprovedMemory(title, content, cat, tags)
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Saved approved memory #$id: '$title'.",
            verified = id > 0,
        )
    }
}
