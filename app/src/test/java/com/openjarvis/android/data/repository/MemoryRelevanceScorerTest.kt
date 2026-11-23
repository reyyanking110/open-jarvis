package com.openjarvis.android.data.repository

import com.google.common.truth.Truth.assertThat
import com.openjarvis.android.core.domain.model.MemoryCategory
import com.openjarvis.android.core.domain.model.MemoryEntry
import org.junit.Test

class MemoryRelevanceScorerTest {

    @Test
    fun `ranks title and tag match higher than weak body match`() {
        val queryTokens = MemoryRelevanceScorer.tokenize("favorite coffee morning")
        val coffeeMem = MemoryEntry(
            id = 1L,
            title = "Morning Coffee Preference",
            content = "User drinks black espresso at 8am.",
            category = MemoryCategory.PREFERENCE,
            tags = "coffee, morning",
            salienceBoost = 2,
        )
        val unrelatedMem = MemoryEntry(
            id = 2L,
            title = "Server IP",
            content = "Backup runs every morning at 2am.",
            category = MemoryCategory.FACT,
            tags = "infra",
            salienceBoost = 0,
        )

        val s1 = MemoryRelevanceScorer.score(queryTokens, coffeeMem)
        val s2 = MemoryRelevanceScorer.score(queryTokens, unrelatedMem)
        assertThat(s1).isGreaterThan(s2)
    }

    @Test
    fun `builds sanitized FTS4 prefix query`() {
        val fts = MemoryRelevanceScorer.buildFtsQuery("What's my wifi-router password?!")
        assertThat(fts).contains("wifi*")
        assertThat(fts).contains("router*")
        assertThat(fts).contains("password*")
        assertThat(fts).doesNotContain("?")
    }
}
