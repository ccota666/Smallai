package com.mimochat.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MiMoApiException(message: String) : Exception(message)

sealed class ChatPart {
    data class Text(val text: String) : ChatPart()
    data class ImageUrl(val url: String) : ChatPart()
}

data class ApiMessage(val role: String, val parts: List<ChatPart>)

/** 联网搜索引用来源 */
data class Citation(
    val url: String,
    val title: String = "",
    val siteName: String = "",
    val summary: String = "",
    val publishTime: String = ""
)

data class StreamResult(
    val content: String,
    val reasoning: String,
    val citations: List<Citation> = emptyList()
)

class MiMoApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming, no read timeout
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentCall: Call? = null

    /** 已接收的部分正文/思考/引用，供取消后落库，避免丢失。 */
    @Volatile
    var lastContent: String = ""
        private set

    @Volatile
    var lastReasoning: String = ""
        private set

    @Volatile
    var lastCitations: List<Citation> = emptyList()
        private set

    fun cancel() {
        runCatching { currentCall?.cancel() }
    }

    suspend fun streamChat(
        settings: AppSettings,
        messages: List<ApiMessage>,
        onContentDelta: (String) -> Unit,
        onReasoningDelta: (String) -> Unit
    ): StreamResult = withContext(Dispatchers.IO) {
        lastContent = ""
        lastReasoning = ""
        lastCitations = emptyList()
        val call = client.newCall(buildRequest(settings, messages))
        currentCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    throw MiMoApiException("HTTP ${resp.code} ${extractError(err)}")
                }
                val source = resp.body?.source() ?: throw MiMoApiException("空响应")
                val contentSb = StringBuilder()
                val reasoningSb = StringBuilder()
                val citations = LinkedHashMap<String, Citation>()
                try {
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            if (data.isEmpty()) continue
                            runCatching { JSONObject(data) }.getOrNull()?.let { obj ->
                                val choices = obj.optJSONArray("choices")
                                val choice = choices?.optJSONObject(0)
                                val delta = choice?.optJSONObject("delta")
                                val contentObj = delta?.opt("content")
                                if (contentObj is String && contentObj.isNotEmpty()) {
                                    contentSb.append(contentObj)
                                    lastContent = contentSb.toString()
                                    onContentDelta(contentObj)
                                }
                                // 兼容 reasoning_content / reasoning 两种字段名
                                val reasoningObj = delta?.opt("reasoning_content")
                                    ?: delta?.opt("reasoning")
                                if (reasoningObj is String && reasoningObj.isNotEmpty()) {
                                    reasoningSb.append(reasoningObj)
                                    lastReasoning = reasoningSb.toString()
                                    onReasoningDelta(reasoningObj)
                                }
                                // 联网搜索引用：delta.annotations / message.annotations
                                collectAnnotations(delta?.optJSONArray("annotations"), citations)
                                collectAnnotations(
                                    choice?.optJSONObject("message")?.optJSONArray("annotations"),
                                    citations
                                )
                                // 网页搜索失败时的错误信息
                                val searchErr = delta?.optString("error_message", "")
                                if (!searchErr.isNullOrBlank()) {
                                    // 保留为正文尾注，避免静默失败
                                    contentSb.append("\n\n> 联网搜索出错：$searchErr")
                                    lastContent = contentSb.toString()
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (coroutineContext.isActive) throw e
                    // 用户取消：保留已接收内容，由上层落库
                }
                lastCitations = citations.values.toList()
                StreamResult(contentSb.toString(), reasoningSb.toString(), citations.values.toList())
            }
        } finally {
            currentCall = null
        }
    }

    private fun collectAnnotations(arr: JSONArray?, out: MutableMap<String, Citation>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            if (a.optString("type") != "url_citation") continue
            val url = a.optString("url")
            if (url.isBlank() || out.containsKey(url)) continue
            out[url] = Citation(
                url = url,
                title = a.optString("title"),
                siteName = a.optString("site_name"),
                summary = a.optString("summary"),
                publishTime = a.optString("publish_time")
            )
        }
    }

    private fun buildRequest(settings: AppSettings, messages: List<ApiMessage>): Request {
        val body = JSONObject()
        body.put("model", settings.model)
        body.put("stream", true)
        // 官方默认 131072，思考+正文共用；过小会在长题上被截断
        body.put("max_completion_tokens", 131072)
        // 深度思考开启时服务端会强制 temperature=1.0 / top_p=0.95，不传避免「设了无效」的困惑
        if (!settings.deepThinking) {
            body.put("temperature", settings.temperature.coerceIn(0f, 1.5f).toDouble())
        }
        body.put("thinking", JSONObject().put("type", if (settings.deepThinking) "enabled" else "disabled"))
        if (settings.webSearch) {
            body.put(
                "tools",
                JSONArray().put(
                    JSONObject()
                        .put("type", "web_search")
                        .put("max_keyword", 3)
                        .put("force_search", true)
                        .put("limit", 5)
                )
            )
            body.put("tool_choice", "auto")
        }
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", systemPrompt(settings)))
        for (m in messages) {
            arr.put(JSONObject().put("role", m.role).put("content", contentToJson(m.parts)))
        }
        body.put("messages", arr)
        val url = settings.baseUrl.trimEnd('/') + "/chat/completions"
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("api-key", settings.apiKey)
            .addHeader("Authorization", "Bearer " + settings.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun contentToJson(parts: List<ChatPart>): Any {
        if (parts.size == 1 && parts[0] is ChatPart.Text) {
            return (parts[0] as ChatPart.Text).text
        }
        val arr = JSONArray()
        for (p in parts) {
            when (p) {
                is ChatPart.Text -> arr.put(JSONObject().put("type", "text").put("text", p.text))
                is ChatPart.ImageUrl -> arr.put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", p.url))
                )
            }
        }
        return arr
    }

    private fun systemPrompt(settings: AppSettings): String =
        settings.systemPrompt.ifBlank {
            "你是MiMo，小米公司研发的AI智能助手。请优先使用简体中文回答。"
        }

    private fun extractError(body: String): String = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message") ?: body
    }.getOrDefault(body)
}
