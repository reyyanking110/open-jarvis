package com.openjarvis.android.ai.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalToolCallParserTest {

    @Test
    fun `extracts fenced JSON tool call`() {
        val raw = """
            Sure, turning on the flashlight now.
            ```json
            {"tool": "set_flashlight", "args": {"enabled": true}}
            ```
        """.trimIndent()

        val parsed = LocalToolCallParser.extractToolCall(raw)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.name).isEqualTo("set_flashlight")
        assertThat(parsed?.arguments?.get("enabled").toString()).isEqualTo("true")
    }

    @Test
    fun `extracts inline JSON tool call`() {
        val raw = """{"tool":"get_device_info","args":{}}"""
        val parsed = LocalToolCallParser.extractToolCall(raw)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.name).isEqualTo("get_device_info")
    }

    @Test
    fun `returns null for ordinary conversational text`() {
        val parsed = LocalToolCallParser.extractToolCall("Your battery is looking healthy today.")
        assertThat(parsed).isNull()
    }
}
