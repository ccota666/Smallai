package com.mimochat.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PromptDefaults {
    const val SYSTEM = "你是MiMo，小米公司研发的AI智能助手。请优先使用简体中文回答。"
    const val COMPRESS = """请把下面这段历史对话压缩为「前情提要」，不超过 300 字。
保留：具体事实、数字、结论、用户偏好、未解决的问题。
丢弃：寒暄、重复表述、助手的思考过程。
用第三人称客观陈述，不要输出任何解释。"""
}

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.xiaomimimo.com/v1",
    val models: String = "mimo-v2.6-flash,**mimo-v2.6-pro-ultraspeed**,mimo-v2.6-pro",
    val model: String = "mimo-v2.6-flash",
    val themeMode: Int = 2, // 0 = light, 1 = dark, 2 = system
    val fontSize: Float = 16f,
    val contextLimit: Int = 40,
    val systemPrompt: String = "",
    val webSearch: Boolean = false,
    val deepThinking: Boolean = true,
    /** API 范围 [0, 1.5]；深度思考开启时服务端会强制 1.0 */
    val temperature: Float = 1.0f,
    /** 加粗/关键词颜色 ARGB，0 表示跟随主题 */
    val boldColor: Int = 0,
    val maxTokens: Int = 131072,
    /** 超出 contextLimit 的历史，是否在下次提问前压缩为摘要 */
    val compressOverflow: Boolean = false,
    /** 压缩提示词（可自定义） */
    val compressPrompt: String = PromptDefaults.COMPRESS,
    val systemPromptDefault: String = PromptDefaults.SYSTEM
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODELS = stringPreferencesKey("models")
        val MODEL = stringPreferencesKey("model")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val CONTEXT_LIMIT = intPreferencesKey("context_limit")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val WEB_SEARCH = booleanPreferencesKey("web_search")
        val DEEP_THINKING = booleanPreferencesKey("deep_thinking")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val BOLD_COLOR = intPreferencesKey("bold_color")
        val COMPRESS_OVERFLOW = booleanPreferencesKey("compress_overflow")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[Keys.API_KEY] ?: "",
            baseUrl = p[Keys.BASE_URL] ?: "https://api.xiaomimimo.com/v1",
            models = p[Keys.MODELS] ?: "mimo-v2.6-flash,**mimo-v2.6-pro-ultraspeed**,mimo-v2.6-pro",
            model = p[Keys.MODEL] ?: "mimo-v2.6-flash",
            themeMode = p[Keys.THEME_MODE] ?: 2,
            fontSize = p[Keys.FONT_SIZE] ?: 16f,
            contextLimit = p[Keys.CONTEXT_LIMIT] ?: 40,
            systemPrompt = p[Keys.SYSTEM_PROMPT] ?: PromptDefaults.SYSTEM,
            webSearch = p[Keys.WEB_SEARCH] ?: false,
            deepThinking = p[Keys.DEEP_THINKING] ?: true,
            temperature = (p[Keys.TEMPERATURE] ?: 1.0f).coerceIn(0f, 1.5f),
            boldColor = p[Keys.BOLD_COLOR] ?: 0,
            maxTokens = 131072,
            compressOverflow = p[Keys.COMPRESS_OVERFLOW] ?: false,
            compressPrompt = p[Keys.COMPRESS_PROMPT] ?: PromptDefaults.COMPRESS
        )
    }

    suspend fun setApiKey(v: String) = context.dataStore.edit { it[Keys.API_KEY] = v }
    suspend fun setBaseUrl(v: String) = context.dataStore.edit { it[Keys.BASE_URL] = v }
    suspend fun setModels(v: String) = context.dataStore.edit { it[Keys.MODELS] = v }
    suspend fun setModel(v: String) = context.dataStore.edit { it[Keys.MODEL] = v }
    suspend fun setThemeMode(v: Int) = context.dataStore.edit { it[Keys.THEME_MODE] = v }
    suspend fun setFontSize(v: Float) = context.dataStore.edit { it[Keys.FONT_SIZE] = v }
    suspend fun setContextLimit(v: Int) = context.dataStore.edit { it[Keys.CONTEXT_LIMIT] = v }
    suspend fun setSystemPrompt(v: String) = context.dataStore.edit { it[Keys.SYSTEM_PROMPT] = v }
    suspend fun setWebSearch(v: Boolean) = context.dataStore.edit { it[Keys.WEB_SEARCH] = v }
    suspend fun setDeepThinking(v: Boolean) = context.dataStore.edit { it[Keys.DEEP_THINKING] = v }
    suspend fun setTemperature(v: Float) =
        context.dataStore.edit { it[Keys.TEMPERATURE] = v.coerceIn(0f, 1.5f) }

    suspend fun setBoldColor(v: Int) = context.dataStore.edit { it[Keys.BOLD_COLOR] = v }
    suspend fun setCompressOverflow(v: Boolean) =
        context.dataStore.edit { it[Keys.COMPRESS_OVERFLOW] = v }
    suspend fun setCompressPrompt(v: String) =
        context.dataStore.edit { it[Keys.COMPRESS_PROMPT] = v }
}

/** 模型列表解析：**name** 表示高价模型（显示红色），name 为普通 */
object ModelsParser {
    data class ModelItem(val id: String, val expensive: Boolean)

    fun parse(modelsCsv: String): List<ModelItem> {
        return modelsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { raw ->
                val t = raw.trim()
                if (t.startsWith("**") && t.endsWith("**") && t.length > 4) {
                    ModelItem(t.substring(2, t.length - 2).trim(), true)
                } else {
                    ModelItem(t, false)
                }
            }
    }

    fun isExpensive(modelsCsv: String, id: String): Boolean {
        return parse(modelsCsv).any { it.id == id && it.expensive }
    }

    /** 存回 csv 时保留 ** 标记 */
    fun toCsv(items: List<ModelItem>): String {
        return items.joinToString(",") {
            if (it.expensive) "**${it.id}**" else it.id
        }
    }
}
