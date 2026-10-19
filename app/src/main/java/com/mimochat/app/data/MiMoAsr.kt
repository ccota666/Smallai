package com.mimochat.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MiMo-V2.5-ASR 语音识别。
 *
 * 官方是 OpenAI Chat Completions 兼容，不是 /audio/transcriptions：
 *   POST {base}/chat/completions
 *   model: mimo-v2.5-asr
 *   messages[0].content = [{type:"input_audio", input_audio:{data:"data:audio/wav;base64,..."}}]
 * 音频仅支持 mp3 / wav。
 */
class MiMoAsr {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        settings: AppSettings,
        audioFile: File,
        language: String = "zh"
    ): String = withContext(Dispatchers.IO) {
        val mime = if (audioFile.extension.equals("mp3", true)) "audio/mpeg" else "audio/wav"
        val b64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
        val dataUrl = "data:$mime;base64,$b64"

        val body = JSONObject().apply {
            put("model", "mimo-v2.5-asr")
            put("stream", false)
            put("asr_options", JSONObject().put("language", language))
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "input_audio")
                                .put("input_audio", JSONObject().put("data", dataUrl))
                        )
                    )
                )
            )
        }

        val url = settings.baseUrl.trimEnd('/') + "/chat/completions"
        val req = Request.Builder()
            .url(url)
            .addHeader("api-key", settings.apiKey)
            .addHeader("Authorization", "Bearer " + settings.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw.take(200)
                }.getOrDefault(raw.take(200))
                throw MiMoApiException("ASR HTTP ${resp.code} $msg")
            }
            val json = JSONObject(raw)
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?: throw MiMoApiException("ASR 返回无内容")
        }
    }
}
