package com.mimochat.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimochat.app.data.AppSettings
import com.mimochat.app.data.ChatRepository
import com.mimochat.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchHit(
    val conversationId: Long,
    val messageId: Long,
    val conversationTitle: String,
    val snippet: String
)

class ConversationListViewModel(
    private val repo: ChatRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    val conversations = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 会话id -> 首条用户问题（两行预览第一行） */
    val firstUser = repo.observeFirstUserMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 会话id -> 首条 AI 回答（问题只有一行时用） */
    val firstAssistant = repo.observeFirstAssistantMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    suspend fun newConversation(): Long = repo.createConversation(settings.value.model)

    fun delete(ids: List<Long>) {
        viewModelScope.launch { repo.deleteConversations(ids) }
    }

    fun rename(id: Long, title: String) {
        viewModelScope.launch { repo.renameConversation(id, title) }
    }

    suspend fun search(query: String): List<SearchHit> {
        val msgs = repo.searchMessages(query)
        return msgs.map { m ->
            SearchHit(
                conversationId = m.conversationId,
                messageId = m.id,
                conversationTitle = repo.getConversation(m.conversationId)?.title ?: "对话",
                snippet = m.content
            )
        }
    }
}
