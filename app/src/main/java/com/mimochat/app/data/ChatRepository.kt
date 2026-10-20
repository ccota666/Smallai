package com.mimochat.app.data

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatRepository(
    private val db: AppDatabase,
    private val settingsRepo: SettingsRepository
) {
    private val api = MiMoApi()

    fun observeConversations() = db.conversationDao().observeAllWithMessages()
    fun observeConversation(id: Long) = db.conversationDao().observeById(id)
    fun observeMessages(convId: Long) = db.messageDao().observeByConversation(convId)

    fun cancelGeneration() = api.cancel()

    /** 打开会话时把残留 pending 落盘，避免一直「正在思考」 */
    suspend fun clearStalePending(convId: Long) {
        val pendings = db.messageDao().getByConversation(convId).filter { it.pending }
        for (m in pendings) {
            db.messageDao().update(
                m.copy(
                    pending = false,
                    content = m.content.ifBlank { "(已中断)" },
                    reasoning = m.reasoning
                )
            )
        }
    }

    fun observeFirstUserMessages() = db.messageDao().observeFirstUserMessages()
    fun observePreviews() = db.messageDao().observePreviews()
    fun observeFirstAssistantMessages() = db.messageDao().observeFirstAssistantMessages()

    suspend fun searchMessages(query: String): List<ChatMessage> =
        db.messageDao().searchMessages(query)

    suspend fun getConversation(id: Long): Conversation? = db.conversationDao().getById(id)

    suspend fun renameConversation(id: Long, title: String) {
        db.conversationDao().updateTitle(id, title, System.currentTimeMillis())
    }

    suspend fun createConversation(model: String): Long {
        val now = System.currentTimeMillis()
        return db.conversationDao().insert(
            Conversation(title = "新对话", model = model, createdAt = now, updatedAt = now)
        )
    }

    suspend fun deleteConversations(ids: List<Long>) {
        for (id in ids) {
            db.conversationDao().deleteMessages(id)
            db.conversationDao().deleteById(id)
        }
    }

    suspend fun updateModel(convId: Long, model: String) {
        db.conversationDao().getById(convId)?.let {
            db.conversationDao().update(it.copy(model = model))
        }
    }

    suspend fun sendMessage(
        convId: Long,
        text: String,
        attachments: List<Attachment>,
        model: String,
        onContentDelta: (String) -> Unit,
        onReasoningDelta: (String) -> Unit
    ): Boolean {
        val now = System.currentTimeMillis()
        val attJson = attachmentsToJson(attachments)
        db.messageDao().insert(
            ChatMessage(conversationId = convId, role = "user", content = text, attachmentsJson = attJson, createdAt = now)
        )
        val placeholderId = db.messageDao().insert(
            ChatMessage(conversationId = convId, role = "assistant", content = "", attachmentsJson = "", createdAt = now + 1, pending = true)
        )
        val settings = settingsRepo.settings.first().copy(model = model,
            systemPrompt = lastCompressedSystem.ifBlank { settingsRepo.settings.first().systemPrompt })
        val history = buildHistory(convId, placeholderId)
        val requestStart = System.currentTimeMillis()
        var firstContentAt = 0L
        return try {
            val result = api.streamChat(settings, history,
                onContentDelta = {
                    if (firstContentAt == 0L) firstContentAt = System.currentTimeMillis()
                    onContentDelta(it)
                },
                onReasoningDelta = onReasoningDelta
            )
            val thinkingMs = (if (firstContentAt > 0) firstContentAt else System.currentTimeMillis()) - requestStart
            db.messageDao().getById(placeholderId)?.let {
                db.messageDao().update(
                    it.copy(
                        content = result.content,
                        reasoning = result.reasoning,
                        citationsJson = citationsToJson(result.citations),
                        pending = false,
                        failed = false,
                        thinkingMs = thinkingMs
                    )
                )
            }
            maybeSetTitle(convId)
            true
        } catch (e: CancellationException) {
            // 用户停止：在不可取消上下文里把已接收内容落库
            withContext(NonCancellable) {
                db.messageDao().getById(placeholderId)?.let {
                    db.messageDao().update(
                        it.copy(
                            content = api.lastContent.ifBlank { "(已停止)" },
                            reasoning = api.lastReasoning,
                            citationsJson = citationsToJson(api.lastCitations),
                            pending = false,
                            failed = false,
                            thinkingMs = (if (firstContentAt > 0) firstContentAt else System.currentTimeMillis()) - requestStart
                        )
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            db.messageDao().getById(placeholderId)?.let {
                db.messageDao().update(
                    it.copy(content = "出错了：${e.message}", pending = false, failed = true)
                )
            }
            false
        }
    }

    suspend fun regenerate(
        convId: Long,
        assistantMessageId: Long,
        model: String,
        onContentDelta: (String) -> Unit,
        onReasoningDelta: (String) -> Unit
    ): Boolean {
        val msgs = db.messageDao().getByConversation(convId)
        val idx = msgs.indexOfFirst { it.id == assistantMessageId }
        if (idx < 0) return false
        val hasUser = (idx - 1 downTo 0).any { msgs[it].role == "user" }
        if (!hasUser) return false
        db.messageDao().deleteFromId(convId, assistantMessageId)
        val now = System.currentTimeMillis()
        val placeholderId = db.messageDao().insert(
            ChatMessage(conversationId = convId, role = "assistant", content = "", attachmentsJson = "", createdAt = now, pending = true)
        )
        val settings = settingsRepo.settings.first().copy(model = model,
            systemPrompt = lastCompressedSystem.ifBlank { settingsRepo.settings.first().systemPrompt })
        val history = buildHistory(convId, placeholderId)
        val requestStart = System.currentTimeMillis()
        var firstContentAt = 0L
        return try {
            val result = api.streamChat(settings, history,
                onContentDelta = {
                    if (firstContentAt == 0L) firstContentAt = System.currentTimeMillis()
                    onContentDelta(it)
                },
                onReasoningDelta = onReasoningDelta
            )
            val thinkingMs = (if (firstContentAt > 0) firstContentAt else System.currentTimeMillis()) - requestStart
            db.messageDao().getById(placeholderId)?.let {
                db.messageDao().update(
                    it.copy(
                        content = result.content,
                        reasoning = result.reasoning,
                        citationsJson = citationsToJson(result.citations),
                        pending = false,
                        failed = false,
                        thinkingMs = thinkingMs
                    )
                )
            }
            true
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                db.messageDao().getById(placeholderId)?.let {
                    db.messageDao().update(
                        it.copy(
                            content = api.lastContent.ifBlank { "(已停止)" },
                            reasoning = api.lastReasoning,
                            citationsJson = citationsToJson(api.lastCitations),
                            pending = false,
                            failed = false,
                            thinkingMs = (if (firstContentAt > 0) firstContentAt else System.currentTimeMillis()) - requestStart
                        )
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            db.messageDao().getById(placeholderId)?.let {
                db.messageDao().update(
                    it.copy(content = "出错了：${e.message}", pending = false, failed = true)
                )
            }
            false
        }
    }

    suspend fun deletePair(convId: Long, assistantMessageId: Long) {
        val msgs = db.messageDao().getByConversation(convId)
        val idx = msgs.indexOfFirst { it.id == assistantMessageId }
        if (idx < 0) return
        val userIdx = (idx - 1 downTo 0).firstOrNull { msgs[it].role == "user" }
        val startId = if (userIdx != null) msgs[userIdx].id else msgs[idx].id
        db.messageDao().deleteFromId(convId, startId)
    }

    suspend fun finalizePartial(convId: Long, partial: String, partialReasoning: String) {
        val pending = db.messageDao().getByConversation(convId).lastOrNull { it.pending }
        if (pending != null) {
            db.messageDao().update(
                pending.copy(
                    content = partial.ifBlank { "(已停止)" },
                    reasoning = partialReasoning,
                    citationsJson = citationsToJson(api.lastCitations),
                    pending = false
                )
            )
            maybeSetTitle(convId)
        }
    }

    private suspend fun maybeSetTitle(convId: Long) {
        val msgs = db.messageDao().getByConversation(convId)
        val userMsgs = msgs.filter { it.role == "user" && it.content.isNotBlank() }
        if (userMsgs.size == 1) {
            val title = userMsgs[0].content.trim().take(30).ifBlank { "新对话" }
            db.conversationDao().updateTitle(convId, title, System.currentTimeMillis())
        }
    }

    private fun toApiMessages(list: List<ChatMessage>): List<ApiMessage> {
        return list.map { m ->
            val parts = mutableListOf<ChatPart>()
            if (m.content.isNotBlank()) parts.add(ChatPart.Text(m.content))
            for (a in parseAttachments(m.attachmentsJson)) {
                if (a.type == "image" && a.path != null) {
                    val f = java.io.File(a.path)
                    if (f.exists()) {
                        val b64 = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
                        parts.add(ChatPart.ImageUrl("data:image/jpeg;base64,$b64"))
                    }
                }
            }
            ApiMessage(m.role, parts)
        }
    }

    private suspend fun buildHistory(convId: Long, excludeId: Long): List<ApiMessage> {
        val settings = settingsRepo.settings.first()
        val all = db.messageDao().getByConversation(convId)
            .filter { it.id != excludeId && !it.pending && it.content.isNotBlank() }
        val limit = settings.contextLimit.coerceAtLeast(4)
        if (!settings.compressOverflow || all.size <= limit) {
            // 不压缩：直接截最近 limit 条
            return toApiMessages(all.takeLast(limit))
        }
        // 超出 limit → 用户本次提问前，先把「上文」压缩成摘要
        val older = all.dropLast(limit)
        val recent = all.takeLast(limit)
        val summary = runCatching {
            api.completeOnce(
                settings,
                settings.compressPrompt,
                older.joinToString("\n") {
                    (if (it.role == "user") "用户：" else "助手：") + it.content
                }
            )
        }.getOrDefault("")
        val sys = settings.systemPrompt + (
            if (summary.isBlank()) "" else "\n\n【前情提要】\n$summary"
        )
        // 摘要走 system，最近消息原样
        val list = toApiMessages(recent).toMutableList()
        // buildRequest 会再加 system，这里用 messages 前插一条 system 可绕过
        // 简化：把摘要拼进第一条 user 前的 system —— 直接改 settings 传递
        lastCompressedSystem = sys
        return list
    }

    @Volatile private var lastCompressedSystem: String = ""

    private fun base64Image(path: String): String? = runCatching {
        val file = File(path)
        if (!file.exists()) return@runCatching null
        val bytes = file.readBytes()
        "data:${mimeFromName(file.name)};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    private fun mimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "image/png"
        }
    }

    companion object {
        const val IMAGE_HINT = "\n\n【图片】请根据你之前对该图片的回答来判断其内容；如果无法确定图片内容，请告诉用户重新上传该图片。"

        fun attachmentsToJson(list: List<Attachment>): String {
            if (list.isEmpty()) return ""
            val arr = JSONArray()
            for (a in list) {
                val o = JSONObject()
                o.put("type", a.type)
                a.path?.let { o.put("path", it) }
                a.name?.let { o.put("name", it) }
                a.content?.let { o.put("content", it) }
                arr.put(o)
            }
            return arr.toString()
        }

        fun parseAttachments(json: String): List<Attachment> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Attachment(
                        type = o.optString("type"),
                        path = o.optString("path").ifBlank { null },
                        name = o.optString("name").ifBlank { null },
                        content = o.optString("content").ifBlank { null }
                    )
                }
            }.getOrDefault(emptyList())
        }

        fun citationsToJson(list: List<Citation>): String {
            if (list.isEmpty()) return ""
            val arr = JSONArray()
            for (c in list) {
                arr.put(
                    JSONObject()
                        .put("url", c.url)
                        .put("title", c.title)
                        .put("site_name", c.siteName)
                        .put("summary", c.summary)
                        .put("publish_time", c.publishTime)
                )
            }
            return arr.toString()
        }

        fun parseCitations(json: String): List<Citation> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Citation(
                        url = o.optString("url"),
                        title = o.optString("title"),
                        siteName = o.optString("site_name"),
                        summary = o.optString("summary"),
                        publishTime = o.optString("publish_time")
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
