package com.mimochat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 历史压缩后的「前情提要」 */
    val contextSummary: String = "",
    /** 摘要已覆盖到哪条消息 id */
    val summaryUpTo: Long = 0L,
    val mode: String = "work"
)

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val attachmentsJson: String = "",
    val createdAt: Long,
    val pending: Boolean = false,
    val failed: Boolean = false,
    val thinkingMs: Long = 0,
    val citationsJson: String = ""
)

data class Attachment(
    val type: String,
    val path: String? = null,
    val name: String? = null,
    val content: String? = null
)
