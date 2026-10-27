package com.mimochat.app.ui.termux

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.delay
import java.io.File

/**
 * 在 proot Ubuntu 里调 mimo run，产物落到 /sdcard/Download/MiMoChat。
 *
 * 做法：把 bash 脚本 base64 后写盘，再交给 proot 执行 —— 彻底避开引号转义。
 */
object MimoRunner {
    /** 实时状态（来自 mimo_run.log 尾行） */
    val statusFlow = kotlinx.coroutines.flow.MutableStateFlow<String>("")

    /** Termux / App 侧真实可写路径（bridge_log 已验证） */
    const val HOST_DIR = "/storage/emulated/0/Download/MiMoChat"
    /** proot Ubuntu 内路径（通过 --bind /storage/emulated/0:/sdcard 映射） */
    const val GUEST_DIR = "/sdcard/Download/MiMoChat"
    private const val SCRIPT_NAME = "mimo_task.sh"
    private const val LOG_NAME = "mimo_run.log"
    private const val MIMO_BIN = "/root/.mimocode/bin/mimo"

    /** 聊天指令：/mimo <任务描述> */
    fun parse(text: String): String? {
        val t = text.trim()
        if (!t.startsWith("/mimo")) return null
        val task = t.removePrefix("/mimo").trim()
            .trimEnd('、', '，', '。', '；', '！', '？', ',', '.', ';', '!', '?')
        return task.ifBlank { null }
    }

    /** 生成 ubuntu 内执行的 bash 脚本内容 */
    fun buildScript(task: String): String {
        val q = task.replace("'", "'\\''")
        return listOf(
            "#!/bin/bash",
            "cd $GUEST_DIR",
            "export PATH=/root/.mimocode/bin:\${PATH}",
            "echo START \$(date +%s) > $LOG_NAME",
            "echo IN_UBUNTU_OK >> /sdcard/Download/MiMoChat/bridge_log.txt",
            "echo TASK_DIR=/sdcard/Download/MiMoChat >> $LOG_NAME",
            // 清掉可能挂着的旧进程，避免锁死；stdin 给 /dev/null；限 180s
            "pkill -f 'mimo run' 2>/dev/null || true",
            "timeout 180 $MIMO_BIN run --yolo '请务必在 /sdcard/Download/MiMoChat 目录下完成，所有文件写入该绝对路径。任务：$q' < /dev/null 2>&1 | tee -a $LOG_NAME",
            "echo EXIT_CODE=\${PIPESTATUS[0]} >> $LOG_NAME",
            "echo END \$(date +%s) >> $LOG_NAME"
        ).joinToString("\n")
    }

    /**
     * 下发任务：
     *  1) Termux bash 用 base64 把脚本写进共享目录（无引号问题）
     *  2) proot Ubuntu 执行脚本
     */
    fun start(context: Context, task: String): Boolean {
        val script = buildScript(task)
        val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val write = "mkdir -p $HOST_DIR && echo $b64 | base64 -d > $HOST_DIR/$SCRIPT_NAME && chmod +x $HOST_DIR/$SCRIPT_NAME"
        // 与手动验证过的命令完全一致：用 host 路径当脚本路径
        val run = "echo WRAP_START >> $HOST_DIR/bridge_log.txt && " +
            "proot-distro login ubuntu --bind /storage/emulated/0:/sdcard -- /bin/bash $HOST_DIR/$SCRIPT_NAME && " +
            "echo WRAP_OK >> $HOST_DIR/bridge_log.txt || echo WRAP_FAIL >> $HOST_DIR/bridge_log.txt"
        TermuxBridge.writeLog(context, "MIMO-SETUP(len=${b64.length}): ok")
        // 不自动唤醒 Termux：直接后台命令，失败由调用方提示用户检查 Termux
        val ok1 = TermuxBridge.runInBackground(context, write)
        if (!ok1) {
            TermuxBridge.writeLog(context, "MIMO-SETUP FAIL")
            return false
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            TermuxBridge.runInBackground(context, "sleep 1 && $run")
        }, 800)
        TermuxBridge.writeLog(context, "MIMO-RUN dispatched")
        return true
    }

    /** 把 mimo_run.log 的一行翻译成人话 */
    fun parseLogLine(line: String): String? {
        val t = line.trim()
        if (t.isBlank()) return null
        return when {
            t.startsWith("START ") -> "🚀 开始执行"
            t.startsWith("END ") -> "🏁 执行结束"
            t.startsWith("EXIT_CODE=") -> "退出码 " + t.substringAfter("=")
            t.contains("> build") -> "🚀 Agent 启动（" + t.substringAfter("· ").trim() + "）"
            t.startsWith("Creating ") -> "📝 规划中：${t.removePrefix("Creating ").take(40)}"
            t.startsWith("← Write ") -> {
                val path = t.removePrefix("← Write ").trim()
                val name = path.substringAfterLast("/")
                "✍️ 正在写入 $name"
            }
            t.startsWith("Wrote file") -> "✅ 文件写入完成"
            t.startsWith("已创建") -> "✔ " + t.take(30)
            t.startsWith("$ ") -> "🔧 ${'$'}" + t.removePrefix("$ ").take(40)
            t.startsWith("TASK_DIR") -> null
            t.startsWith("IN_UBUNTU") -> null
            t.startsWith("Warning") -> null
            else -> if (t.length > 2) "· " + t.take(50) else null
        }
    }

    /** 任务是否中断：有 START 无 END */
    fun isInterrupted(logText: String): Boolean {
        val t = logText.trim()
        return t.contains("START ") && !t.contains("END ") && !t.contains("EXIT_CODE")
    }

    /** 中断当前 mimo 任务 */
    fun cancel(context: Context): Boolean {
        val cmd = "proot-distro login ubuntu --bind /storage/emulated/0:/sdcard -- " +
            "/bin/bash -lc 'pkill -f \"mimo run\" 2>/dev/null; echo CANCELLED >> /sdcard/Download/MiMoChat/mimo_run.log'"
        return TermuxBridge.runInBackground(context, cmd)
    }

    fun appOutDir(context: Context): File = File(HOST_DIR)

    fun snapshot(context: Context): Set<String> {
        return appOutDir(context).listFiles()?.map { it.name }?.toSet() ?: emptySet()
    }

    /**
     * 轮询产物目录：按「修改时间 > 开始时刻」找文件。
     * 不能只看新增文件名 —— 手动跑过一次后，同名产物已在 baseline 里。
     */
    suspend fun awaitNewFiles(
        context: Context,
        startTimeMs: Long,
        timeoutMs: Long = 90_000,
        pollMs: Long = 1500
    ): List<File> {
        val dir = appOutDir(context)
        val ignore = setOf(SCRIPT_NAME, LOG_NAME, ".task_txt", "bridge_log.txt",
            "serve_token.json", "serve_token_new.json", "diag.txt", "diag2.txt", "diag_cmd.sh")
        // 产物优先级：文档 > 图片 > 其它；源码/脚本不当产物
        val skipExt = setOf("py", "js", "sh", "json", "log", "txt.bak", "tmp", "class")
        val docExt = setOf("docx", "doc", "pptx", "ppt", "xlsx", "xls", "pdf", "md", "txt", "csv", "html")
        var waited = 0L
        while (waited < timeoutMs) {
            delay(pollMs)
            waited += pollMs
            val hit = dir.listFiles()?.filter {
                it.isFile &&
                    it.name !in ignore &&
                    it.extension.lowercase() !in skipExt &&   // 排除源码脚本
                    it.length() > 0 &&
                    it.lastModified() >= startTimeMs - 2000
            } ?: emptyList()
            if (hit.isNotEmpty()) {
                // 文档优先返回
                return hit.sortedBy { f ->
                    val e = f.extension.lowercase()
                    when {
                        e in docExt -> 0
                        e in setOf("png", "jpg", "jpeg", "webp", "gif") -> 1
                        else -> 2
                    }
                }
            }
        }
        return emptyList()
    }
}
