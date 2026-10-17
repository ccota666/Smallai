package com.mimochat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long
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
