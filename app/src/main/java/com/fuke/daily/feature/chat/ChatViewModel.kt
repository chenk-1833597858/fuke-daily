package com.fuke.daily.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════
//  聊天 ViewModel
// ═══════════════════════════════════════════════════

data class ChatUiState(
    val currentConversationId: Long = 0,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val hasActiveConfig: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val apiConfigDao: ApiConfigDao,
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao,
) : ViewModel() {

    private val gson = Gson()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val conversations = conversationDao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiConfigs = apiConfigDao.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var streamJob: Job? = null
    private var currentMessages: List<ChatMessage> = emptyList()

    init {
        // 检查是否有激活的API配置
        viewModelScope.launch {
            val activeConfig = apiConfigDao.getActiveConfig()
            _uiState.update { it.copy(hasActiveConfig = activeConfig != null) }
        }
    }

    // ═══════════════════════════════════════════════════
    //  对话管理
    // ═══════════════════════════════════════════════════

    fun createConversation(title: String = "新对话", systemPrompt: String = "") {
        viewModelScope.launch {
            val activeConfig = apiConfigDao.getActiveConfig()
            val id = conversationDao.insertConversation(
                Conversation(
                    title = title,
                    apiConfigId = activeConfig?.id ?: 0,
                    systemPrompt = systemPrompt,
                )
            )
            selectConversation(id)
        }
    }

    fun selectConversation(conversationId: Long) {
        streamJob?.cancel()
        _uiState.update { it.copy(currentConversationId = conversationId, isStreaming = false) }

        viewModelScope.launch {
            chatMessageDao.getMessages(conversationId).collect { messages ->
                currentMessages = messages
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationDao.deleteConversation(conversation)
            if (_uiState.value.currentConversationId == conversation.id) {
                _uiState.update { it.copy(currentConversationId = 0, messages = emptyList()) }
            }
        }
    }

    fun updateConversationTitle(conversationId: Long, title: String) {
        viewModelScope.launch {
            val conv = conversationDao.getConversation(conversationId) ?: return@launch
            conversationDao.updateConversation(conv.copy(title = title))
        }
    }

    // ═══════════════════════════════════════════════════
    //  发送消息
    // ═══════════════════════════════════════════════════

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val conversationId = _uiState.value.currentConversationId
        if (conversationId == 0L) return

        _uiState.update { it.copy(inputText = "", isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val activeConfig = apiConfigDao.getActiveConfig()
                if (activeConfig == null) {
                    _uiState.update { it.copy(error = "请先配置API", isLoading = false) }
                    return@launch
                }

                // 1. 保存用户消息
                val userMessage = ChatMessage(
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    content = text,
                )
                chatMessageDao.insertMessage(userMessage)

                // 2. 构建API请求
                val messages = buildApiMessages(conversationId, activeConfig)
                val request = ChatRequest(
                    model = activeConfig.model,
                    messages = messages,
                    stream = true,
                    max_tokens = activeConfig.maxTokens,
                    temperature = activeConfig.temperature,
                    tools = if (ToolRegistry.hasTools()) ToolRegistry.getSchemas() else null,
                )

                // 3. 创建AI消息占位
                val aiMessage = ChatMessage(
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    isStreaming = true,
                )
                val aiMessageId = chatMessageDao.insertMessage(aiMessage)

                // 4. 流式请求
                val apiClient = ChatApiClient(activeConfig.baseUrl, activeConfig.apiKey)
                _uiState.update { it.copy(isStreaming = true) }

                streamJob = viewModelScope.launch {
                    val toolCallsAccumulator = mutableListOf<ToolCallChunk>()
                    var fullContent = ""

                    apiClient.streamChat(request).collect { chunk ->
                        // 文本内容
                        if (chunk.content.isNotEmpty()) {
                            fullContent += chunk.content
                            chatMessageDao.updateMessage(
                                aiMessage.copy(id = aiMessageId, content = fullContent, isStreaming = true)
                            )
                        }

                        // 工具调用
                        if (chunk.toolCalls.isNotEmpty()) {
                            toolCallsAccumulator.addAll(chunk.toolCalls)
                        }

                        // 完成
                        if (chunk.isDone) {
                            if (toolCallsAccumulator.isNotEmpty()) {
                                // AI请求调用工具
                                chatMessageDao.updateMessage(
                                    aiMessage.copy(
                                        id = aiMessageId,
                                        content = fullContent,
                                        isStreaming = false,
                                        toolCalls = gson.toJson(toolCallsAccumulator.map { tc ->
                                            ToolCallData(
                                                id = tc.id,
                                                function = FunctionCallData(
                                                    name = tc.name,
                                                    arguments = tc.arguments,
                                                )
                                            )
                                        })
                                    )
                                )

                                // 执行每个工具调用
                                for (tc in toolCallsAccumulator) {
                                    val args: Map<String, Any> = try {
                                        gson.fromJson(tc.arguments, object : TypeToken<Map<String, Any>>() {}.type)
                                    } catch (e: Exception) {
                                        emptyMap()
                                    }
                                    val result = ToolRegistry.execute(tc.name, args)

                                    // 保存工具结果消息
                                    chatMessageDao.insertMessage(
                                        ChatMessage(
                                            conversationId = conversationId,
                                            role = MessageRole.TOOL,
                                            content = result,
                                            toolCallId = tc.id,
                                        )
                                    )
                                }

                                // 工具执行完毕，再次请求AI（让AI根据工具结果继续回复）
                                handleToolCallContinue(conversationId, activeConfig, aiMessageId)
                            } else {
                                // 普通回复完成
                                chatMessageDao.updateMessage(
                                    aiMessage.copy(id = aiMessageId, content = fullContent, isStreaming = false)
                                )
                            }

                            _uiState.update { it.copy(isStreaming = false, isLoading = false) }
                        }
                    }
                }

                // 更新对话时间戳
                conversationDao.updateTimestamp(conversationId)

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "发送失败：${e.message}", isLoading = false, isStreaming = false) }
            }
        }
    }

    /**
     * 工具调用后继续请求AI
     */
    private suspend fun handleToolCallContinue(
        conversationId: Long,
        config: ApiConfig,
        originalAiMessageId: Long,
    ) {
        val messages = buildApiMessages(conversationId, config)
        val request = ChatRequest(
            model = config.model,
            messages = messages,
            stream = true,
            max_tokens = config.maxTokens,
            temperature = config.temperature,
            tools = if (ToolRegistry.hasTools()) ToolRegistry.getSchemas() else null,
        )

        // 创建新的AI消息占位
        val aiMessage = ChatMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
        )
        val aiMessageId = chatMessageDao.insertMessage(aiMessage)

        val apiClient = ChatApiClient(config.baseUrl, config.apiKey)
        var fullContent = ""

        apiClient.streamChat(request).collect { chunk ->
            if (chunk.content.isNotEmpty()) {
                fullContent += chunk.content
                chatMessageDao.updateMessage(
                    aiMessage.copy(id = aiMessageId, content = fullContent, isStreaming = true)
                )
            }
            if (chunk.isDone) {
                chatMessageDao.updateMessage(
                    aiMessage.copy(id = aiMessageId, content = fullContent, isStreaming = false)
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  构建API消息列表
    // ═══════════════════════════════════════════════════

    private suspend fun buildApiMessages(conversationId: Long, config: ApiConfig): List<ChatRequestMessage> {
        val messages = mutableListOf<ChatRequestMessage>()

        // 系统提示词（对话级优先，否则用API配置级的）
        val conv = conversationDao.getConversation(conversationId)
        val systemPrompt = conv?.systemPrompt?.takeIf { it.isNotBlank() } ?: config.systemPrompt
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatRequestMessage(role = "system", content = systemPrompt))
        }

        // 历史消息
        val dbMessages = chatMessageDao.getMessagesList(conversationId)
        for (msg in dbMessages) {
            when (msg.role) {
                MessageRole.USER -> {
                    messages.add(ChatRequestMessage(role = "user", content = msg.content))
                }
                MessageRole.ASSISTANT -> {
                    val toolCalls: List<ToolCallData>? = if (msg.toolCalls.isNotBlank()) {
                        try {
                            gson.fromJson(msg.toolCalls, object : TypeToken<List<ToolCallData>>() {}.type)
                        } catch (e: Exception) { null }
                    } else null

                    messages.add(ChatRequestMessage(
                        role = "assistant",
                        content = msg.content,
                        toolCalls = toolCalls,
                    ))
                }
                MessageRole.TOOL -> {
                    messages.add(ChatRequestMessage(
                        role = "tool",
                        content = msg.content,
                        toolCallId = msg.toolCallId,
                    ))
                }
                MessageRole.SYSTEM -> {
                    messages.add(ChatRequestMessage(role = "system", content = msg.content))
                }
            }
        }

        return messages
    }

    // ═══════════════════════════════════════════════════
    //  API 配置管理
    // ═══════════════════════════════════════════════════

    fun saveApiConfig(config: ApiConfig) {
        viewModelScope.launch {
            if (config.isActive) {
                apiConfigDao.deactivateAll()
            }
            if (config.id == 0L) {
                apiConfigDao.insertConfig(config)
            } else {
                apiConfigDao.updateConfig(config)
            }
            val activeConfig = apiConfigDao.getActiveConfig()
            _uiState.update { it.copy(hasActiveConfig = activeConfig != null) }
        }
    }

    fun deleteApiConfig(config: ApiConfig) {
        viewModelScope.launch {
            apiConfigDao.deleteConfig(config)
            val activeConfig = apiConfigDao.getActiveConfig()
            _uiState.update { it.copy(hasActiveConfig = activeConfig != null) }
        }
    }

    fun activateApiConfig(id: Long) {
        viewModelScope.launch {
            apiConfigDao.deactivateAll()
            apiConfigDao.activateConfig(id)
            _uiState.update { it.copy(hasActiveConfig = true) }
        }
    }

    // ═══════════════════════════════════════════════════
    //  UI 交互
    // ═══════════════════════════════════════════════════

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        _uiState.update { it.copy(isStreaming = false, isLoading = false) }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
