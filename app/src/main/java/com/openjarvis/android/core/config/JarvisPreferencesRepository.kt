package com.openjarvis.android.core.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openjarvis.android.core.domain.model.InferenceMode
import com.openjarvis.android.core.domain.model.LocalAccelerator
import com.openjarvis.android.core.domain.model.ModelCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

data class JarvisSettings(
    val inferenceMode: InferenceMode = InferenceMode.AUTO,
    val selectedGeminiModelId: String = ModelCatalog.geminiModels.first().id,
    val selectedLocalModelId: String = ModelCatalog.localModels.first().id,
    val localAccelerator: LocalAccelerator = LocalAccelerator.GPU,
    val autoSpeakResponses: Boolean = false,
    val privacyLocalOnly: Boolean = false,
    val maxAgentSteps: Int = 8,
    val activeConversationId: Long = 1L,
    val safTreeUri: String? = null,
)

@Singleton
class JarvisPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val INFERENCE_MODE = stringPreferencesKey("inference_mode")
        val GEMINI_MODEL_ID = stringPreferencesKey("gemini_model_id")
        val LOCAL_MODEL_ID = stringPreferencesKey("local_model_id")
        val LOCAL_ACCELERATOR = stringPreferencesKey("local_accelerator")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val PRIVACY_LOCAL_ONLY = booleanPreferencesKey("privacy_local_only")
        val MAX_AGENT_STEPS = intPreferencesKey("max_agent_steps")
        val ACTIVE_CONVERSATION_ID = longPreferencesKey("active_conversation_id")
        val SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        JarvisSettings(
            inferenceMode = prefs[Keys.INFERENCE_MODE]
                ?.let { runCatching { InferenceMode.valueOf(it) }.getOrNull() }
                ?: InferenceMode.AUTO,
            selectedGeminiModelId = prefs[Keys.GEMINI_MODEL_ID]
                ?: ModelCatalog.geminiModels.first().id,
            selectedLocalModelId = prefs[Keys.LOCAL_MODEL_ID]
                ?: ModelCatalog.localModels.first().id,
            localAccelerator = prefs[Keys.LOCAL_ACCELERATOR]
                ?.let { runCatching { LocalAccelerator.valueOf(it) }.getOrNull() }
                ?: LocalAccelerator.GPU,
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: false,
            privacyLocalOnly = prefs[Keys.PRIVACY_LOCAL_ONLY] ?: false,
            maxAgentSteps = (prefs[Keys.MAX_AGENT_STEPS] ?: 8).coerceIn(2, 16),
            activeConversationId = prefs[Keys.ACTIVE_CONVERSATION_ID] ?: 1L,
            safTreeUri = prefs[Keys.SAF_TREE_URI],
        )
    }

    suspend fun setInferenceMode(mode: InferenceMode) {
        context.jarvisDataStore.edit { it[Keys.INFERENCE_MODE] = mode.name }
    }

    suspend fun setGeminiModel(modelId: String) {
        context.jarvisDataStore.edit { it[Keys.GEMINI_MODEL_ID] = modelId }
    }

    suspend fun setLocalModel(modelId: String) {
        context.jarvisDataStore.edit { it[Keys.LOCAL_MODEL_ID] = modelId }
    }

    suspend fun setLocalAccelerator(accelerator: LocalAccelerator) {
        context.jarvisDataStore.edit { it[Keys.LOCAL_ACCELERATOR] = accelerator.name }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setPrivacyLocalOnly(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.PRIVACY_LOCAL_ONLY] = enabled }
    }

    suspend fun setMaxAgentSteps(steps: Int) {
        context.jarvisDataStore.edit { it[Keys.MAX_AGENT_STEPS] = steps.coerceIn(2, 16) }
    }

    suspend fun setActiveConversationId(conversationId: Long) {
        context.jarvisDataStore.edit { it[Keys.ACTIVE_CONVERSATION_ID] = conversationId }
    }

    suspend fun setSafTreeUri(uriString: String?) {
        context.jarvisDataStore.edit { prefs ->
            if (uriString.isNullOrBlank()) {
                prefs.remove(Keys.SAF_TREE_URI)
            } else {
                prefs[Keys.SAF_TREE_URI] = uriString
            }
        }
    }
}
