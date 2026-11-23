package com.openjarvis.android.tools

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class ToolRegistry @Inject constructor(
    tools: Set<@JvmSuppressWildcards AndroidTool>,
) {
    private val toolMap: Map<String, AndroidTool> = tools.associateBy { it.spec.name }

    fun allTools(): List<AndroidTool> = toolMap.values.sortedBy { it.spec.name }

    fun get(name: String): AndroidTool? = toolMap[name]

    /**
     * Generates Gemini API `functionDeclarations` JSON array for online function calling.
     */
    fun toGeminiFunctionDeclarations(): JsonArray = buildJsonArray {
        allTools().forEach { tool ->
            add(
                buildJsonObject {
                    put("name", tool.spec.name)
                    put("description", tool.spec.description)
                    put(
                        "parameters",
                        buildJsonObject {
                            put("type", "object")
                            put(
                                "properties",
                                buildJsonObject {
                                    tool.spec.parameters.forEach { param ->
                                        put(
                                            param.name,
                                            buildJsonObject {
                                                put("type", param.type)
                                                put("description", param.description)
                                                if (param.enumValues.isNotEmpty()) {
                                                    put(
                                                        "enum",
                                                        JsonArray(param.enumValues.map { JsonPrimitive(it) }),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                            )
                            val required = tool.spec.parameters.filter { it.required }.map { it.name }
                            if (required.isNotEmpty()) {
                                put("required", JsonArray(required.map { JsonPrimitive(it) }))
                            }
                        },
                    )
                }
            )
        }
    }

    /**
     * Compact tool catalog prompt for local LiteRT-LM offline models.
     */
    fun toCompactPromptSpec(): String = buildString {
        appendLine("Available Android tools (call ONLY when needed by emitting JSON {\"tool\":\"name\",\"args\":{...}}):")
        allTools().forEach { tool ->
            val params = tool.spec.parameters.joinToString(", ") {
                val req = if (it.required) "*" else ""
                "${it.name}$req:${it.type}"
            }
            appendLine("- ${tool.spec.name}($params): ${tool.spec.description}")
        }
    }
}
