package com.openjarvis.android.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openjarvis.android.core.config.ApiKeyProvider
import com.openjarvis.android.core.config.JarvisPreferencesRepository
import com.openjarvis.android.core.config.JarvisSettings
import com.openjarvis.android.core.domain.model.InferenceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: JarvisSettings = JarvisSettings(),
    val hasGeminiKey: Boolean = false,
    val maskedKeyHint: String = "",
    val speechRecognitionAvailable: Boolean = false,
    val statusNotice: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: JarvisPreferencesRepository,
    private val apiKeyProvider: ApiKeyProvider,
) : ViewModel() {

    private val statusNotice = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settingsFlow,
        apiKeyProvider.apiKeyConfiguredFlow,
        statusNotice,
    ) { settings, configured, notice ->
        val rawKey = apiKeyProvider.getGeminiApiKey()
        val masked = if (rawKey.length >= 8) {
            "${rawKey.take(4)}••••••••${rawKey.takeLast(4)}"
        } else if (rawKey.isNotBlank()) {
            "••••••••"
        } else {
            ""
        }
        SettingsUiState(
            settings = settings,
            hasGeminiKey = configured,
            maskedKeyHint = masked,
            speechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context),
            statusNotice = notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun saveApiKey(key: String) {
        apiKeyProvider.saveGeminiApiKey(key)
        statusNotice.value = "Gemini API key encrypted in Android Keystore."
    }

    fun clearApiKey() {
        apiKeyProvider.clearGeminiApiKey()
        statusNotice.value = "Stored Gemini API key cleared."
    }

    fun setInferenceMode(mode: InferenceMode) {
        viewModelScope.launch { preferencesRepository.setInferenceMode(mode) }
    }

    fun setPrivacyLocalOnly(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPrivacyLocalOnly(enabled) }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoSpeak(enabled) }
    }

    fun setMaxSteps(steps: Int) {
        viewModelScope.launch { preferencesRepository.setMaxAgentSteps(steps) }
    }

    fun saveSafWorkspaceTree(uri: Uri?) {
        if (uri == null) return
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        viewModelScope.launch {
            preferencesRepository.setSafWorkspaceTree(uri)
        }
    }

    private suspend fun JarvisPreferencesRepository.setSafWorkspaceTree(uri: Uri) {
        setSafTreeUri(uri.toString())
        statusNotice.value = "SAF workspace folder linked."
    }

    fun clearSafWorkspace() {
        viewModelScope.launch {
            preferencesRepository.setSafTreeUri(null)
            statusNotice.value = "SAF workspace folder unlinked."
        }
    }

    fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun clearNotice() {
        statusNotice.value = null
    }
}
