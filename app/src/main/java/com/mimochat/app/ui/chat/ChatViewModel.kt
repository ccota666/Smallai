package com.mimochat.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimochat.app.data.AppSettings
import com.mimochat.app.data.Attachment
import com.mimochat.app.data.ChatMessage
import com.mimochat.app.data.ChatRepository
import com.mimochat.app.data.Conversation
import com.mimochat.app.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repo: ChatRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {


    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation = _conversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _streaming = MutableStateFlow("")
    val streaming = _streaming.asStateFlow()

    private val _reasoning = MutableStateFlow("")
    val reasoning = _reasoning.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking = _isThinking.asStateFlow()

    private val _thinkingSeconds = MutableStateFlow(0f)
    val thinkingSeconds = _thinkingSeconds.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val apiStatus = repo.apiStatus

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var generationJob: Job? = null
    private var thinkingStartMs = 0L
    private var tickerJob: Job? = null

    // 流式缓冲：避免 String += 的 O(n²) 拼接与每 delta 全量重组
    private val contentSb = StringBuilder()
    private val reasoningSb = StringBuilder()
    private var lastPublishAt = 0L

    var conversationId: Long = -1L
        private set

    suspend fun insertLocalMessage(convId: Long, role: String, content: String) =
        repo.insertLocalMessage(convId, role, content)

    suspend fun insertLocalTask(content: String): Long =
        repo.insertLocalMessage(conversationId, "assistant", "[TASK]\n$content", pending = true)

    suspend fun updateLocalTask(id: Long, content: String, attachmentsJson: String? = null) =
        repo.updateMessageContent(id, "[TASK]\n$content", attachmentsJson)

    fun init(convId: Long) {
        // 注意顺序：先判重再赋值，否则 observer 永远不会注册（消息全不显示）
        if (conversationId == convId && _messages.value.isNotEmpty()) return
        conversationId = convId
        viewModelScope.launch { runCatching { repo.clearStalePending(convId) } }
        viewModelScope.launch {
            repo.observeConversation(convId).collect { _conversation.value = it }
        }
        viewModelScope.launch {
            repo.observeMessages(convId).collect { _messages.value = it }
        }
    }

    fun send(text: String, attachments: List<Attachment>) {
        if ((text.isBlank() && attachments.isEmpty()) || _isGenerating.value) return
        if (settings.value.apiKey.isBlank()) {
            _error.value = "请先在设置中填写 API Key"
            return
        }
        val model = _conversation.value?.model ?: settings.value.model
        _isGenerating.value = true
        generationJob = viewModelScope.launch {
            beginThinking()
            val ok = repo.sendMessage(conversationId, text, attachments, model,
                onContentDelta = { delta -> onContent(delta) },
                onReasoningDelta = { delta -> onReasoning(delta) }
            )
            endThinking()
            if (!ok) _error.value = "生成失败，请检查 API Key 与网络"
            _isGenerating.value = false
        }
    }

    fun regenerate(assistantMessageId: Long) {
        if (_isGenerating.value) return
        if (settings.value.apiKey.isBlank()) {
            _error.value = "请先在设置中填写 API Key"
            return
        }
        val model = _conversation.value?.model ?: settings.value.model
        _isGenerating.value = true
        generationJob = viewModelScope.launch {
            beginThinking()
            val ok = repo.regenerate(conversationId, assistantMessageId, model,
                onContentDelta = { delta -> onContent(delta) },
                onReasoningDelta = { delta -> onReasoning(delta) }
            )
            endThinking()
            if (!ok) _error.value = "重新生成失败"
            _isGenerating.value = false
        }
    }

    fun stop() {
        // 取消后由 ChatRepository 在 NonCancellable 里落库，避免双写竞态
        generationJob?.cancel()
        repo.cancelGeneration()
        _isGenerating.value = false
        tickerJob?.cancel()
        _isThinking.value = false
        publishStreaming(force = true)
    }

    fun deletePair(assistantMessageId: Long) {
        viewModelScope.launch { repo.deletePair(conversationId, assistantMessageId) }
    }

    fun setModel(model: String) {
        viewModelScope.launch { repo.updateModel(conversationId, model) }
    }

    fun setWebSearch(v: Boolean) = viewModelScope.launch { settingsRepo.setWebSearch(v) }
    fun setDeepThinking(v: Boolean) = viewModelScope.launch { settingsRepo.setDeepThinking(v) }
    fun setTemperature(v: Float) = viewModelScope.launch { settingsRepo.setTemperature(v) }

    fun clearError() {
        _error.value = null
    }

    private fun beginThinking() {
        thinkingStartMs = System.currentTimeMillis()
        contentSb.setLength(0)
        reasoningSb.setLength(0)
        lastPublishAt = 0L
        _streaming.value = ""
        _reasoning.value = ""
        _isThinking.value = true
        _thinkingSeconds.value = 0f
        _error.value = null
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                _thinkingSeconds.value = (System.currentTimeMillis() - thinkingStartMs) / 1000f
                delay(100)
            }
        }
    }

    private fun onContent(delta: String) {
        if (_isThinking.value) {
            _isThinking.value = false
            tickerJob?.cancel()
            _thinkingSeconds.value = (System.currentTimeMillis() - thinkingStartMs) / 1000f
        }
        contentSb.append(delta)
        publishStreaming()
    }

    private fun onReasoning(delta: String) {
        reasoningSb.append(delta)
        publishStreaming()
    }

    private fun publishStreaming(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPublishAt < 50) return
        lastPublishAt = now
        _streaming.value = contentSb.toString()
        _reasoning.value = reasoningSb.toString()
    }

    private fun endThinking() {
        tickerJob?.cancel()
        _isThinking.value = false
        _thinkingSeconds.value = (System.currentTimeMillis() - thinkingStartMs) / 1000f
        publishStreaming(force = true)
        contentSb.setLength(0)
        reasoningSb.setLength(0)
    }
}
