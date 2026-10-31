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
    private const val SCRIPT_NAME = "mimo_task.sh" // 兼容旧名
    private const val LOG_NAME = "mimo_run.log"
    private const val MIMO_BIN = "/root/.mimocode/bin/mimo"

    /** 聊天指令：/mimo <任务描述> */
    fun convDirHost(convId: Long) = "$HOST_DIR/conv_$convId"
    fun convDirGuest(convId: Long) = "$GUEST_DIR/conv_$convId"

    /** 环境探活：能写文件到共享目录 = Termux 真的活着 */
    suspend fun probe(context: Context, timeoutMs: Long = 4000): Boolean {
        val f = File(appOutDir(context), "bridge_probe.txt")
        f.delete()
        val ok = TermuxBridge.runInBackground(context, "echo PONG > $HOST_DIR/bridge_probe.txt")
        if (!ok) return false
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < timeoutMs) {
            kotlinx.coroutines.delay(300)
            if (f.exists() && f.readText().contains("PONG")) {
                f.delete()
                return true
            }
        }
        return false
    }

    fun parse(text: String): String? {
        val t = text.trim()
        if (!t.startsWith("/mimo")) return null
        val task = t.removePrefix("/mimo").trim()
            .trimEnd('、', '，', '。', '；', '！', '？', ',', '.', ';', '!', '?')
        return task.ifBlank { null }
    }

        /** 日志里除了脚手架（echo/migration/EXIT_CODE）外是否有真实任务输出 */
    fun hasRealOutput(logText: String): Boolean {
        val cleaned = logText.replace(Regex("\\x1B\\[[0-9;]*[A-Za-z]"), "")
        val boilerplate = setOf(
            "START ", "END ", "PWD=", "WORK=", "EXIT_CODE=", "CANCELLED",
            "WRAP_START", "WRAP_OK", "WRAP_FAIL",
            "Performing one time database migration",
            "sqlite-migration:done",
            "Database migration complete"
        )
        return cleaned.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { line -> boilerplate.none { bp -> line.startsWith(bp) || line == bp } }
    }

/** 生成 ubuntu 内执行的 bash 脚本内容 */
    fun buildScript(task: String, taskId: Long = 0L, model: String = "", convId: Long = 0L, files: List<String> = emptyList()): String {
        val q = task.replace("'", "'\\''")
        // 文件附件：拼进任务提示词
        val fileHint = if (files.isNotEmpty()) {
            "\\n\\n参考文件（已在 uploads/ 目录）：" + files.joinToString("、") { "uploads/$it" }
        } else ""
        val taskFull = task + fileHint
        val q2 = taskFull.replace("'", "'\\''")
        val log = if (taskId != 0L) "mimo_run_$taskId.log" else "mimo_run.log"
        val pidf = "mimo_pid_$taskId"
        val cid = if (convId != 0L) convId else taskId
        val work = convDirGuest(cid)
        val cliModel = if (model.isBlank()) "xiaomi/mimo-v2.6-pro"
            else if (model.contains("/")) model
            else "xiaomi/$model"
        val modelFlag = "--model $cliModel "
        return listOf(
            "#!/bin/bash",
            "mkdir -p $work",
            "cd $work",
            "export PATH=/root/.mimocode/bin:${'$'}{PATH}",
            // 每对话独立 MIMOCODE_HOME：记忆隔离
            // config/state 用 symlink 指向全局（登录凭证/模型），data 独立
            // MIMOCODE_HOME 必须在 proot 文件系统（/ext4 支持 symlink）
            // /sdcard/ 是 FAT 不支持 symlink，所以放 /root/ 下
            "export MIMOCODE_HOME=/root/.mimohome/conv_$cid",
            "mkdir -p ${'$'}MIMOCODE_HOME/data/memory ${'$'}MIMOCODE_HOME/cache",
            // symlink config/state/data 除 memory 外的内容（认证、模型、插件）
            "ln -sfn /root/.config/mimocode ${'$'}MIMOCODE_HOME/config",
            "ln -sfn /root/.local/state/mimocode ${'$'}MIMOCODE_HOME/state",
            "for f in /root/.local/share/mimocode/*; do bn=${'$'}(basename ${'$'}f); if [ \"${'$'}bn\" != \"memory\" ]; then ln -sfn ${'$'}f ${'$'}MIMOCODE_HOME/data/${'$'}bn; fi; done",
            "echo START ${'$'}(date +%s) > $GUEST_DIR/$log",
            "echo PWD=${'$'}(pwd) >> $GUEST_DIR/$log",
            "echo WORK=$work >> $GUEST_DIR/$log",
            "echo HOME=${'$'}MIMOCODE_HOME >> $GUEST_DIR/$log",
            // 模型格式: provider/model，缺 provider 补 xiaomi/
            "timeout 300 $MIMO_BIN run --yolo " + modelFlag +
                "'请把产出文件保存到当前工作目录。任务：$q2' < /dev/null 2>&1 | tee -a $GUEST_DIR/$log &",
            "echo ${'$'}! > $GUEST_DIR/$pidf",
            "wait",
            "find /root /tmp $work -maxdepth 4 -type f -mmin -20 2>/dev/null | while read f; do case ${'$'}f in *.md|*.txt|*.docx|*.pptx|*.xlsx|*.pdf|*.csv|*.html|*.png|*.jpg) cp -n \"${'$'}f\" $work/ 2>/dev/null ;; esac; done",
            "echo EXIT_CODE=${'$'}{PIPESTATUS[0]} >> $GUEST_DIR/$log",
            "echo END ${'$'}(date +%s) >> $GUEST_DIR/$log",
            "rm -f $GUEST_DIR/$pidf"
        ).joinToString("\n")
    }


    fun logFile(context: Context, taskId: Long): File =
        File(appOutDir(context), "mimo_run_$taskId.log")

    fun start(context: Context, task: String, taskId: Long = System.currentTimeMillis(), model: String = "", convId: Long = 0L, files: List<String> = emptyList()): Boolean {
        val script = buildScript(task, taskId, model, convId, files)
        val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val scriptName = "mimo_task_$taskId.sh"
        val write = "mkdir -p $HOST_DIR && echo $b64 | base64 -d > $HOST_DIR/$scriptName && chmod +x $HOST_DIR/$scriptName"
        // 与手动验证过的命令完全一致：用 host 路径当脚本路径
        val run = "echo WRAP_START >> $HOST_DIR/bridge_log.txt && " +
            "proot-distro login ubuntu --bind /storage/emulated/0:/sdcard -- /bin/bash $HOST_DIR/$scriptName && " +
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

    /**
     * 日志按「提示语 / 命令 / 输出」分组：
     *   [0m          → 新提示语（agent 的一句话说明）
     *   [0m$ <cmd>   → 执行的命令
     *   其余行        → 该段输出
     * 返回 List<LogSection(title, lines)>
     */
    data class LogSection(val title: String, val lines: List<String>)

    fun parseLogSections(logText: String): List<LogSection> {
        val cleaned = logText.replace(Regex("\\x1B\\[[0-9;]*m"), "")
        val secs = mutableListOf<LogSection>()
        var title = ""
        val buf = mutableListOf<String>()
        fun flush() {
            if (title.isNotBlank() || buf.isNotEmpty()) {
                secs.add(LogSection(title, buf.toList()))
                buf.clear()
            }
        }
        for (raw in cleaned.lines()) {
            val ln = raw.trimEnd()
            // 命令行：$ ...
            val cmd = ln.trim().removePrefix("$").trim()
            when {
                ln.isBlank() -> { if (buf.isNotEmpty() || title.isNotBlank()) buf.add("") }
                ln.trim() == "$" || ln.trim().startsWith("$ ") -> {
                    buf.add("$ " + cmd)
                }
                // 新提示语行（单独一段自然语言，非命令输出）
                ln.trim().length > 8 && !ln.startsWith(" ") &&
                    !ln.startsWith("$") && !ln.contains("=") &&
                    (ln.contains("。") || ln.contains("已") || ln.contains("生成") ||
                     ln.contains("检查") || ln.contains("注意") || ln.contains("产出") ||
                     ln.contains("**")) -> {
                    flush()
                    title = ln.trim()
                }
                else -> buf.add(ln)
            }
        }
        flush()
        return secs.takeLast(6)
    }

    /** 提取日志结尾的「人话总结」：最后一个 ANSI 块之后到 EXIT_CODE 之前 */
    fun extractSummary(logText: String): String {
        val cleaned = logText.replace(Regex("\\x1B\\[[0-9;]*m"), "")
        val exitIdx = cleaned.lastIndexOf("EXIT_CODE=")
        val body = if (exitIdx > 0) cleaned.substring(0, exitIdx) else cleaned
        // 取最后一段：以空行分隔，找最后一个较长的自然语言段
        val paras = body.split(Regex("\\n\\s*\\n"))
        for (p in paras.reversed()) {
            val t = p.trim()
            // 末段通常是总结（含中文/文件名等），排除命令行回显
            if (t.length > 30 && !t.startsWith("$ ") && !t.startsWith("from ") && !t.startsWith("python")) {
                return t.take(800)
            }
        }
        return body.trim().takeLast(400)
    }

    /** 中断当前 mimo 任务 */
    fun cancel(context: Context, taskId: Long = 0L): Boolean {
        val pidf = "mimo_pid_$taskId"
        val log = if (taskId != 0L) "mimo_run_$taskId.log" else "mimo_run.log"
        // 只杀本任务：按 pid 文件 / 脚本名匹配，不动别的并发任务
        val cmd = "proot-distro login ubuntu --bind /storage/emulated/0:/sdcard -- /bin/bash -lc '" +
            "if [ -f /sdcard/Download/MiMoChat/$pidf ]; then kill ${'$'}(cat /sdcard/Download/MiMoChat/$pidf) 2>/dev/null; fi; " +
            "pkill -f mimo_task_$taskId 2>/dev/null; " +
            "echo CANCELLED >> /sdcard/Download/MiMoChat/$log'"
        return TermuxBridge.runInBackground(context, cmd)
    }

    fun appOutDir(context: Context, convId: Long = 0L): File =
        if (convId != 0L) File(convDirHost(convId)) else File(HOST_DIR)

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
        convId: Long = 0L,
        timeoutMs: Long = 90_000,
        pollMs: Long = 1500
    ): List<File> {
        val dir = appOutDir(context, convId)
        dir.mkdirs()
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
                    it.name !in ignore && !it.name.startsWith("mimo_run_") && !it.name.startsWith("mimo_task_") &&
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
