package com.mimochat.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.screenQaDataStore: DataStore<Preferences> by preferencesDataStore(name = "screen_qa")

/** 屏幕问答方案 */
data class QAPlan(
    val id: Long,
    val name: String,
    val prompt: String,
    /** JPEG 压缩率 10-100，同图片编辑的「压缩率」 */
    val quality: Int = 85
)

data class ScreenQAConfig(
    val enabled: Boolean = false,
    val autoMode: Boolean = false,
    val activePlanId: Long = -1L,
    val plans: List<QAPlan> = emptyList()
)

class ScreenQARepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val AUTO = booleanPreferencesKey("auto_mode")
        val ACTIVE_PLAN = stringPreferencesKey("active_plan_id")
        val PLANS = stringPreferencesKey("plans")
        val LAST_ANSWER = stringPreferencesKey("last_answer")
    }

    val config: Flow<ScreenQAConfig> = context.screenQaDataStore.data.map { p ->
        ScreenQAConfig(
            enabled = p[Keys.ENABLED] ?: false,
            autoMode = p[Keys.AUTO] ?: false,
            activePlanId = p[Keys.ACTIVE_PLAN]?.toLongOrNull() ?: -1L,
            plans = parsePlans(p[Keys.PLANS] ?: "[]")
        )
    }

    suspend fun setEnabled(v: Boolean) = context.screenQaDataStore.edit { it[Keys.ENABLED] = v }
    suspend fun setAutoMode(v: Boolean) = context.screenQaDataStore.edit { it[Keys.AUTO] = v }
    suspend fun setActivePlan(id: Long) =
        context.screenQaDataStore.edit { it[Keys.ACTIVE_PLAN] = id.toString() }

    suspend fun savePlans(plans: List<QAPlan>) =
        context.screenQaDataStore.edit { it[Keys.PLANS] = toJson(plans) }

    suspend fun upsertPlan(plan: QAPlan) {
        val cur = config.first().plans.toMutableList()
        val idx = cur.indexOfFirst { it.id == plan.id }
        if (idx >= 0) cur[idx] = plan else cur.add(plan)
        savePlans(cur)
    }

    suspend fun deletePlan(id: Long) {
        val cur = config.first().plans.filter { it.id != id }
        savePlans(cur)
        if (config.first().activePlanId == id) setActivePlan(-1L)
    }

    suspend fun lastAnswer(): String {
        var v = ""
        context.screenQaDataStore.edit { v = it[Keys.LAST_ANSWER] ?: "" }
        return v
    }

    suspend fun setLastAnswer(s: String) =
        context.screenQaDataStore.edit { it[Keys.LAST_ANSWER] = s }

    private fun toJson(plans: List<QAPlan>): String {
        val arr = JSONArray()
        for (p in plans) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("prompt", p.prompt)
                    .put("quality", p.quality)
            )
        }
        return arr.toString()
    }

    private fun parsePlans(json: String): List<QAPlan> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            QAPlan(
                id = o.optLong("id"),
                name = o.optString("name", "未命名"),
                prompt = o.optString("prompt"),
                quality = o.optInt("quality", 85).coerceIn(10, 100)
            )
        }
    }.getOrDefault(emptyList())
}
