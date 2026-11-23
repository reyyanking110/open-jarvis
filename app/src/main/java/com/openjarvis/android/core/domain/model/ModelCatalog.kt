package com.openjarvis.android.core.domain.model

object ModelCatalog {
    val geminiModels: List<ModelSpec> = listOf(
        ModelSpec(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            engineType = EngineType.GEMINI_CLOUD,
            description = "Fast multimodal & tool-calling cloud model for hybrid orchestration.",
            minRamMb = 0,
            approxSizeMb = 0,
            contextWindow = 1_048_576,
            supportsToolCalling = true,
        ),
        ModelSpec(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            engineType = EngineType.GEMINI_CLOUD,
            description = "High-reasoning cloud model for complex multi-step planning.",
            minRamMb = 0,
            approxSizeMb = 0,
            contextWindow = 1_048_576,
            supportsToolCalling = true,
        ),
    )

    val localModels: List<ModelSpec> = listOf(
        ModelSpec(
            id = "gemma-3-1b-it-int4",
            displayName = "Gemma 3 1B IT (INT4)",
            engineType = EngineType.LOCAL_LITERT_LM,
            description = "Compact 1B on-device LiteRT-LM model (~550 MB) for offline chat and quick commands.",
            minRamMb = 3072,
            approxSizeMb = 555,
            contextWindow = 4096,
            supportsToolCalling = true,
            fileName = "gemma3-1b-it-int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.litertlm",
        ),
        ModelSpec(
            id = "qwen2.5-1.5b-instruct-q8",
            displayName = "Qwen 2.5 1.5B Instruct (Q8)",
            engineType = EngineType.LOCAL_LITERT_LM,
            description = "Strong structured JSON & offline tool-calling model (~1.6 GB).",
            minRamMb = 4096,
            approxSizeMb = 1600,
            contextWindow = 4096,
            supportsToolCalling = true,
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        ),
        ModelSpec(
            id = "gemma-3n-e2b-it-int4",
            displayName = "Gemma 3n E2B IT (INT4)",
            engineType = EngineType.LOCAL_LITERT_LM,
            description = "Flagship mobile-first Gemma 3n model (~3.1 GB) for rich offline reasoning.",
            minRamMb = 6144,
            approxSizeMb = 3100,
            contextWindow = 4096,
            supportsToolCalling = true,
            fileName = "gemma-3n-E2B-it-int4.litertlm",
            downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
        ),
    )

    val allModels: List<ModelSpec> = geminiModels + localModels

    fun findById(id: String): ModelSpec? = allModels.firstOrNull { it.id == id }
}
