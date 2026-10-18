package com.mimochat.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MiMo-V2.5-ASR 语音识别（OpenAI 兼容 audio/transcriptions）。
 * 上传音频文件 → 返回识别文字。
 */
class MiMoAsr {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 上传音频，返回识别文本。 */
    suspend fun transcribe(
        settings: AppSettings,
        audioFile: File,
        model: String = "mimo-v2.5-asr",
        language: String = "zh"
    ): String = withContext(Dispatchers.IO) {
        val url = settings.baseUrl.trimEnd('/') + "/audio/transcriptions"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("language", language)
            .addFormDataPart(
                "file", audioFile.name,
                audioFile.asRequestBody("audio/mpeg".toMediaType())
            )
            .build()
        val req = Request.Builder()
            .url(url)
            .addHeader("api-key", settings.apiKey)
            .addHeader("Authorization", "Bearer " + settings.apiKey)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw
                }.getOrDefault(raw)
                throw MiMoApiException("ASR HTTP ${resp.code} $msg")
            }
            // OpenAI 兼容：{"text": "..."}
            runCatching { JSONObject(raw).optString("text") }
                .getOrNull()?.takeIf { it.isNotBlank() }
                ?: raw.trim()
        }
    }
}
