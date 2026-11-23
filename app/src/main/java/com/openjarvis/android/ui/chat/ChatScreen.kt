package com.openjarvis.android.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openjarvis.android.agent.AgentExecutionPhase
import com.openjarvis.android.core.domain.model.AgentStepStatus
import com.openjarvis.android.core.domain.model.ChatMessage
import com.openjarvis.android.core.domain.model.FeedbackRating
import com.openjarvis.android.core.domain.model.InferenceMode
import com.openjarvis.android.core.domain.model.MemoryCategory
import com.openjarvis.android.core.domain.model.MessageRole
import com.openjarvis.android.core.domain.model.PendingConfirmation
import com.openjarvis.android.ui.theme.JarvisAmber
import com.openjarvis.android.ui.theme.JarvisEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var showConversationsSheet by remember { mutableStateOf(false) }
    var memoryDraftMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput { transcript ->
                inputText = transcript
            }
        }
    }

    LaunchedEffect(state.messages.size, state.agentState.streamingText) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header bar with online/offline status & session actions
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open JARVIS",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        StatusPillRow(
                            isOnline = state.telemetry.isOnline,
                            mode = state.settings.inferenceMode,
                            hasKey = state.hasGeminiKey,
                            hasLocalModel = state.hasLocalModelInstalled,
                            onClickModel = onOpenModels,
                        )
                    }
                    Row {
                        IconButton(onClick = { showConversationsSheet = true }) {
                            Icon(Icons.Default.History, contentDescription = "Sessions")
                        }
                        IconButton(onClick = { viewModel.createNewConversation() }) {
                            Icon(Icons.Default.AddComment, contentDescription = "New Chat")
                        }
                    }
                }

                // Quick mode switcher chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InferenceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.inferenceMode == mode,
                            onClick = { viewModel.setInferenceMode(mode) },
                            label = {
                                Text(
                                    text = when (mode) {
                                        InferenceMode.AUTO -> "Auto"
                                        InferenceMode.ONLINE_GEMINI -> "Gemini"
                                        InferenceMode.OFFLINE_LOCAL -> "Local"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }
        }

        // Setup notice if neither Gemini key nor local model is configured
        if (!state.hasGeminiKey && !state.hasLocalModelInstalled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Configure an Intelligence Engine",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Add a Gemini API key in Settings for online intelligence, or import a .litertlm model in Models for offline use.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onOpenSettings) { Text("API Key") }
                            TextButton(onClick = onOpenModels) { Text("Local Models") }
                        }
                    }
                }
            }
        }

        // Conversation message list
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty() && !state.agentState.isBusy) {
                EmptyWelcomeState(
                    onPromptClick = { sample -> viewModel.sendMessage(sample) },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            onFeedback = { rating -> viewModel.setFeedback(msg, rating) },
                            onSpeak = { viewModel.speakText(msg.content) },
                            onSaveMemory = { memoryDraftMessage = msg },
                        )
                    }

                    if (state.agentState.isBusy) {
                        item(key = "live_agent_card") {
                            LiveAgentProgressCard(
                                phase = state.agentState.phase,
                                streamingText = state.agentState.streamingText,
                                routingReason = state.agentState.activeRouting?.reason,
                                steps = state.agentState.activeSteps,
                                onCancel = { viewModel.cancelActiveTask() },
                            )
                        }
                    }
                }
            }
        }

        // Speech error or listening bar
        AnimatedVisibility(visible = state.speechState.isListening || state.speechState.errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (state.speechState.isListening) {
                            state.speechState.partialText.ifBlank { "Listening..." }
                        } else {
                            state.speechState.errorMessage.orEmpty()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (state.speechState.isListening) viewModel.stopVoiceInput()
                            else viewModel.clearSpeechError()
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        // Composer bar
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedMicButton(
                    isListening = state.speechState.isListening,
                    rmsDb = state.speechState.rmsDb,
                    onClick = {
                        if (state.speechState.isListening) {
                            viewModel.stopVoiceInput()
                        } else if (viewModel.hasAudioPermission()) {
                            viewModel.startVoiceInput { transcript ->
                                inputText = transcript
                            }
                        } else {
                            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask JARVIS or give a device command...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                if (state.agentState.isBusy) {
                    IconButton(
                        onClick = { viewModel.cancelActiveTask() },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val text = inputText
                            inputText = ""
                            viewModel.sendMessage(text)
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog for sensitive/irreversible Android tool calls
    state.agentState.pendingConfirmation?.let { pending ->
        ConfirmationDialog(
            pending = pending,
            onApprove = { viewModel.resolveConfirmation(true) },
            onDeny = { viewModel.resolveConfirmation(false) },
        )
    }

    // Save-to-memory approval dialog
    memoryDraftMessage?.let { msg ->
        SaveMemoryDialog(
            initialContent = msg.content,
            onDismiss = { memoryDraftMessage = null },
            onSave = { title, content, cat ->
                viewModel.saveApprovedMemoryFromMessage(title, content, cat)
                memoryDraftMessage = null
            },
        )
    }

    // Conversation switcher sheet
    if (showConversationsSheet) {
        ModalBottomSheet(onDismissRequest = { showConversationsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Conversations", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                state.conversations.forEach { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.selectConversation(conv.id)
                                showConversationsSheet = false
                            }
                            .background(
                                if (conv.id == state.activeConversationId) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = conv.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${conv.messageCount} msgs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun StatusPillRow(
    isOnline: Boolean,
    mode: InferenceMode,
    hasKey: Boolean,
    hasLocalModel: Boolean,
    onClickModel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (isOnline) JarvisEmerald.copy(alpha = 0.14f)
                    else JarvisAmber.copy(alpha = 0.14f)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (isOnline) JarvisEmerald else JarvisAmber,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.labelMedium,
                color = if (isOnline) JarvisEmerald else JarvisAmber,
            )
        }

        val engineSummary = when {
            mode == InferenceMode.OFFLINE_LOCAL && hasLocalModel -> "Local LiteRT-LM Ready"
            mode == InferenceMode.OFFLINE_LOCAL -> "No Local Model"
            mode == InferenceMode.ONLINE_GEMINI && hasKey -> "Gemini Cloud"
            isOnline && hasKey && hasLocalModel -> "Hybrid Ready"
            isOnline && hasKey -> "Gemini Ready"
            hasLocalModel -> "Offline Ready"
            else -> "Setup Needed"
        }
        Text(
            text = engineSummary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onClickModel),
        )
    }
}

@Composable
private fun EmptyWelcomeState(onPromptClick: (String) -> Unit) {
    val samples = listOf(
        "Check my battery, RAM, and storage status",
        "Set media volume to 50% and turn on the flashlight",
        "Set an alarm for 07:30 labeled Morning Standup",
        "What can you do offline vs online on Android?",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Autonomous Hybrid Android Agent",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Runs online with Gemini and offline on-device with LiteRT-LM. Every sensitive action asks your permission.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        samples.forEach { sample ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPromptClick(sample) },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ) {
                Text(
                    text = sample,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onFeedback: (FeedbackRating) -> Unit,
    onSpeak: () -> Unit,
    onSaveMemory: () -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier
                .widthIn(max = 330.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(18.dp),
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser && (message.engineUsed != null || message.toolSummary != null)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        message.engineUsed?.let { eng ->
                            Text(
                                text = "${eng.displayName}${message.latencyMs.takeIf { it > 0 }?.let { " · ${it}ms" } ?: ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    message.toolSummary?.let { tools ->
                        Text(
                            text = "Tools: $tools",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (!isUser) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onFeedback(FeedbackRating.POSITIVE) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "Helpful",
                                tint = if (message.feedback == FeedbackRating.POSITIVE) {
                                    JarvisEmerald
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = { onFeedback(FeedbackRating.NEGATIVE) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Default.ThumbDown,
                                contentDescription = "Not helpful",
                                tint = if (message.feedback == FeedbackRating.NEGATIVE) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(onClick = onSpeak, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(onClick = onSaveMemory, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = "Save Memory",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAgentProgressCard(
    phase: AgentExecutionPhase,
    streamingText: String,
    routingReason: String?,
    steps: List<com.openjarvis.android.core.domain.model.AgentStep>,
    onCancel: () -> Unit,
) {
    val pulseTransition = rememberInfiniteTransition(label = "agentPulse")
    val alpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (phase) {
                            AgentExecutionPhase.ROUTING -> "Selecting inference engine..."
                            AgentExecutionPhase.THINKING -> "Planning & reasoning..."
                            AgentExecutionPhase.EXECUTING_TOOL -> "Executing Android tool..."
                            AgentExecutionPhase.AWAITING_CONFIRMATION -> "Waiting for your confirmation..."
                            AgentExecutionPhase.STREAMING_ANSWER -> "Responding..."
                            AgentExecutionPhase.IDLE -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    )
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }

            routingReason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when (step.status) {
                                AgentStepStatus.COMPLETED -> JarvisEmerald
                                AgentStepStatus.FAILED -> MaterialTheme.colorScheme.error
                                AgentStepStatus.AWAITING_CONFIRMATION -> JarvisAmber
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${step.stepIndex}. ${step.description}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (streamingText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = streamingText,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AnimatedMicButton(
    isListening: Boolean,
    rmsDb: Float,
    onClick: () -> Unit,
) {
    val scale = if (isListening) {
        (1f + (rmsDb.coerceIn(0f, 12f) / 40f)).coerceIn(1f, 1.3f)
    } else {
        1f
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isListening) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Voice input",
            tint = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ConfirmationDialog(
    pending: PendingConfirmation,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = JarvisAmber) },
        title = { Text("Confirm Sensitive Action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = pending.humanReadableAction,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tool: ${pending.toolName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = pending.riskReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onApprove) { Text("Approve") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Deny")
            }
        },
    )
}

@Composable
private fun SaveMemoryDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String, String, MemoryCategory) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(initialContent.take(500)) }
    var category by remember { mutableStateOf(MemoryCategory.FACT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Approved Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Content") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, content, category) },
                enabled = content.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
