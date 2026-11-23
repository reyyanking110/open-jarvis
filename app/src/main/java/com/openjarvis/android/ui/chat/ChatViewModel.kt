package com.openjarvis.android.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openjarvis.android.agent.AgentLiveState
import com.openjarvis.android.agent.AutonomousAgentEngine
import com.openjarvis.android.core.config.ApiKeyProvider
import com.openjarvis.android.core.config.JarvisPreferencesRepository
import com.openjarvis.android.core.config.JarvisSettings
import com.openjarvis.android.core.domain.model.ChatMessage
import com.openjarvis.android.core.domain.model.ConversationSummary
import com.openjarvis.android.core.domain.model.DeviceTelemetry
import com.openjarvis.android.core.domain.model.FeedbackRating
import com.openjarvis.android.core.domain.model.InferenceMode
import com.openjarvis.android.core.domain.model.MemoryCategory
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import com.openjarvis.android.data.repository.ConversationRepository
import com.openjarvis.android.data.repository.LearningRepository
import com.openjarvis.android.data.repository.MemoryRepository
import com.openjarvis.android.data.repository.ModelRepository
import com.openjarvis.android.service.AgentForegroundService
import com.openjarvis.android.voice.SpeechRecognitionState
import com.openjarvis.android.voice.SpeechRecognizerManager
import com.openjarvis.android.voice.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val activeConversationId: Long = 1L,
    val conversations: List<ConversationSummary> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val settings: JarvisSettings = JarvisSettings(),
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val agentState: AgentLiveState = AgentLiveState(),
    val speechState: SpeechRecognitionState = SpeechRecognitionState(),
    val isSpeakingTts: Boolean = false,
    val hasGeminiKey: Boolean = false,
    val hasLocalModelInstalled: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val agentEngine: AutonomousAgentEngine,
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val learningRepository: LearningRepository,
    private val modelRepository: ModelRepository,
    private val preferencesRepository: JarvisPreferencesRepository,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val apiKeyProvider: ApiKeyProvider,
    private val speechRecognizerManager: SpeechRecognizerManager,
    private val ttsManager: TextToSpeechManager,
) : ViewModel() {

    private val activeConvFlow = preferencesRepository.settingsFlow
    private val messagesFlow = activeConvFlow.flatMapLatest { settings ->
        conversationRepository.observeMessages(settings.activeConversationId)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        preferencesRepository.settingsFlow,
        conversationRepository.observeConversations(),
        messagesFlow,
        telemetryProvider.telemetryFlow,
        agentEngine.liveState,
        speechRecognizerManager.state,
        ttsManager.isSpeaking,
        apiKeyProvider.apiKeyConfiguredFlow,
        modelRepository.observeInstalledModels(),
    ) { values ->
        val settings = values[0] as JarvisSettings
        @Suppress("UNCHECKED_CAST")
        val conversations = values[1] as List<ConversationSummary>
        @Suppress("UNCHECKED_CAST")
        val messages = values[2] as List<ChatMessage>
        val telemetry = values[3] as DeviceTelemetry
        val agentState = values[4] as AgentLiveState
        val speechState = values[5] as SpeechRecognitionState
        val isSpeaking = values[6] as Boolean
        val hasKey = values[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val installedModels = values[8] as List<*>

        ChatUiState(
            activeConversationId = settings.activeConversationId,
            conversations = conversations,
            messages = messages,
            settings = settings,
            telemetry = telemetry,
            agentState = agentState,
            speechState = speechState,
            isSpeakingTts = isSpeaking,
            hasGeminiKey = hasKey,
            hasLocalModelInstalled = installedModels.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    init {
        viewModelScope.launch {
            val settings = preferencesRepository.settingsFlow.first()
            conversationRepository.ensureConversationExists(settings.activeConversationId)
        }
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || uiState.value.agentState.isBusy) return
        val convId = uiState.value.activeConversationId

        viewModelScope.launch {
            AgentForegroundService.start(appContext, "Processing: ${prompt.take(48)}")
            try {
                val reply = agentEngine.handleUserPrompt(convId, prompt)
                if (uiState.value.settings.autoSpeakResponses && reply.content.isNotBlank()) {
                    ttsManager.speak(reply.content)
                }
            } finally {
                AgentForegroundService.stop(appContext)
            }
        }
    }

    fun cancelActiveTask() {
        agentEngine.cancelActiveTask()
    }

    fun resolveConfirmation(approved: Boolean) {
        agentEngine.resolveConfirmation(approved)
    }

    fun setInferenceMode(mode: InferenceMode) {
        viewModelScope.launch {
            preferencesRepository.setInferenceMode(mode)
        }
    }

    fun setFeedback(message: ChatMessage, rating: FeedbackRating) {
        val next = if (message.feedback == rating) FeedbackRating.NONE else rating
        viewModelScope.launch {
            conversationRepository.setFeedback(message.id, next)
            learningRepository.applyFeedback(message.id, next)
        }
    }

    fun saveApprovedMemoryFromMessage(title: String, content: String, category: MemoryCategory) {
        viewModelScope.launch {
            memoryRepository.saveApprovedMemory(
                title = title.ifBlank { "Saved Note" },
                content = content,
                category = category,
            )
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val newId = conversationRepository.createNewConversation("New Session")
            preferencesRepository.setActiveConversationId(newId)
        }
    }

    fun selectConversation(conversationId: Long) {
        viewModelScope.launch {
            preferencesRepository.setActiveConversationId(conversationId)
        }
    }

    fun startVoiceInput(onTranscript: (String) -> Unit) {
        speechRecognizerManager.startListening(onTranscript)
    }

    fun stopVoiceInput() {
        speechRecognizerManager.stopListening()
    }

    fun clearSpeechError() {
        speechRecognizerManager.clearError()
    }

    fun speakText(text: String) {
        ttsManager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun hasAudioPermission(): Boolean = speechRecognizerManager.hasAudioPermission()
}
