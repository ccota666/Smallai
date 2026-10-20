package com.mimochat.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimochat.app.data.AppSettings
import com.mimochat.app.data.ChatRepository
import com.mimochat.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /** 预览消息（首问+首答），合并查询 + 去重，避免每次发消息全列表重绘 */
    val previews = repo.observePreviews()
        .distinctUntilChanged()
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
