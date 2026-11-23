package com.openjarvis.android.tools.native

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonObject.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull ?: str(key)?.toIntOrNull()

internal fun JsonObject.bool(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull ?: str(key)?.toBooleanStrictOrNull()
