package com.mimochat.app.ui.termux

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 智能体任务总管（Application 级，切对话/关页面都继续跑，支持并发）。
 *
 * 每个任务独立 log/脚本：mimo_run_<taskId>.log、mimo_task_<taskId>.sh
 * 运行状态全局广播，供对话列表显示转圈。
 */
object AgentTaskManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Long, Job>()

    data class TaskState(
        val taskId: Long,
        val convId: Long,
        val content: String,
        val running: Boolean
    )

    private val _states = MutableStateFlow<Map<Long, TaskState>>(emptyMap())
    /** taskId -> 状态 */
    val states: StateFlow<Map<Long, TaskState>> = _states

    /** 当前正在运行的 convId 集合（列表页转圈用） */
    val runningConvIds: StateFlow<Set<Long>> = MutableStateFlow(emptySet())

    /** 最近完成的 convId（列表页蓝点用，用时间戳） */
    private val _doneAt = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val doneAt: StateFlow<Map<Long, Long>> = _doneAt

    fun isRunning(convId: Long): Boolean = jobs.values.any { it.isActive }

    /**
     * 启动任务。onMessage(text, attachmentsJson) 回调写消息。
     * 与调用方解耦：调用方销毁也不影响任务。
     */
    fun start(
        context: Context,
        convId: Long,
        taskId: Long,
        task: String,
        onMessage: suspend (text: String, attJson: String?) -> Unit
    ) {
        if (jobs[taskId]?.isActive == true) return
        val appCtx = context.applicationContext
        val job = scope.launch {
            publish(taskId, convId, "⏳ 正在检测 Termux 环境…", true)
            // 先探活：Termux 没开 / 没允许外部应用时立刻失败，不再假装「执行中」
            if (!MimoRunner.probe(appCtx)) {
                publish(taskId, convId, "❌ Termux 未运行：请打开 Termux 后重试", false)
                return@launch
            }
            publish(taskId, convId, "⏳ 任务已下发，正在执行…", true)
            val startTime = System.currentTimeMillis()
            val ok = MimoRunner.start(appCtx, task, taskId)
            if (!ok) {
                publish(taskId, convId, "❌ 无法启动：请检查 Termux 是否允许「外部应用」", false)
                return@launch
            }
            // 轮询日志
            val logFile = MimoRunner.logFile(appCtx, taskId)
            while (true) {
                kotlinx.coroutines.delay(2500)
                val log = runCatching { logFile.readText() }.getOrDefault("")
                if (log.isNotBlank()) {
                    // ANSI reset (ESC[0m) → 显式 <SEG> 标记，JS 靠它切段；
                    // 其余色码去掉，避免污染文本
                    val marked = log.replace(Regex("\\x1B\\[0m"), "\n<SEG>\n")
                        .replace(Regex("\\x1B\\[[0-9;]*[A-Za-z]"), "")
                    val tail = marked.lines().filter { it.isNotBlank() }.takeLast(120)
                    publish(taskId, convId, "⏳ 运行中\n" + tail.joinToString("\n"), true)
                }
                if (log.contains("END ") || log.contains("EXIT_CODE=")) break
            }
            val log = runCatching { logFile.readText() }.getOrDefault("")
            val files = MimoRunner.awaitNewFiles(appCtx, startTime, timeoutMs = 5_000, pollMs = 500)
            val summary = MimoRunner.extractSummary(log)
            val body = when {
                files.isNotEmpty() -> {
                    val names = files.joinToString { it.name }
                    "✅ 任务完成，生成 ${files.size} 个文件：$names" +
                        (if (summary.isBlank()) "" else "\n---\n$summary")
                }
                MimoRunner.isInterrupted(log) -> "⏹ 任务中断"
                else -> "⏳ 超时未检测到产物\n${summary.take(400)}"
            }
            val attJson = if (files.isNotEmpty()) {
                com.mimochat.app.data.ChatRepository.attachmentsToJson(
                    files.map { f ->
                        com.mimochat.app.data.Attachment(
                            type = when (f.extension.lowercase()) {
                                "png", "jpg", "jpeg", "webp" -> "image"
                                "md", "txt", "csv", "json" -> "text"
                                else -> "file"
                            },
                            path = f.absolutePath, name = f.name
                        )
                    }
                )
            } else null
            publish(taskId, convId, body, false)
            onMessage(body, attJson)
        }
        jobs[taskId] = job
        job.invokeOnCompletion {
            jobs.remove(taskId)
            refreshRunning()
        }
        refreshRunning()
    }

    fun cancel(taskId: Long) {
        jobs[taskId]?.cancel()
        jobs.remove(taskId)
        refreshRunning()
    }

    fun cancelAll() {
        jobs.keys.toList().forEach { cancel(it) }
    }

    private fun publish(taskId: Long, convId: Long, content: String, running: Boolean) {
        _states.value = _states.value + (taskId to TaskState(taskId, convId, content, running))
        if (running) {
            _doneAt.value = _doneAt.value - convId
        } else {
            _doneAt.value = _doneAt.value + (convId to System.currentTimeMillis())
        }
        refreshRunning()
    }

    private fun refreshRunning() {
        val s = _states.value.values.filter { it.running }.map { it.convId }.toSet()
        (runningConvIds as MutableStateFlow).value = s
    }
}
