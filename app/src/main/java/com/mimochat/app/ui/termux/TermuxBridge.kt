package com.mimochat.app.ui.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.io.File

/**
 * Termux 桥：让聊天 App 在后台驱动 Termux 跑命令（生成 docx/pptx 等）。
 *
 * 前提（缺一不可）：
 * 1. 手机已安装 Termux
 * 2. Termux → 设置 → Enable "Allow external apps"
 *    （这样别的应用才能发 RUN_COMMAND 让它执行）
 *
 * 成功后命令输出写到共享目录，App 侧 watch 文件即可取回。
 */
object TermuxBridge {

    /** Termux RUN_COMMAND 广播/组件常量 */
    private const val TERMUX_PKG = "com.termux"
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val EXTRA_CMD = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    /**
     * 任务产物目录（Termux 与 App 共用）。
     * 用「公有 Download/MiMoChat」而不是 Android/data/<pkg>/：
     * 安卓 11+ 的分区存储下，Termux 无法写入其它应用的 Android/data 目录。
     * 路径：/sdcard/Download/MiMoChat  ↔  Termux 内 ~/storage/shared/Download/MiMoChat
     */
    fun sharedOutDir(context: Context): File {
        val dir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ),
            "MiMoChat"
        )
        dir.mkdirs()
        return dir
    }

    /** Termux 侧对应路径（需先执行 termux-setup-storage） */
    const val TERMUX_SIDE_PATH = "/data/data/com.termux/files/home/storage/shared/Download/MiMoChat"

    fun isTermuxInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** 一键安装指引：跳 Termux 页 */
    fun openTermuxPage(context: Context) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("market://details?id=$TERMUX_PKG")
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(i) }.onFailure {
            val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                "https://f-droid.org/en/packages/com.termux/"
            )).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(web) }
        }
    }

    fun writeLog(context: Context, msg: String) {
        try {
            val f = File(sharedOutDir(context), "bridge_log.txt")
            f.appendText(System.currentTimeMillis().toString().takeLast(8) + " " + msg + "\n")
        } catch (_: Exception) {}
    }

    /**
     * 唤醒 Termux 进程（MIUI 后台限制：进程不在时 service 起不来）。
     * 先拉起 Activity 让进程活过来，再延迟发命令。
     */
    fun wakeThenRun(context: Context, command: String, delayMs: Long = 1200L): Boolean {
        try {
            val launch = Intent().apply {
                setClassName(TERMUX_PKG, "com.termux.app.TermuxActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launch)
            writeLog(context, "WAKE termux")
        } catch (e: Exception) {
            writeLog(context, "WAKE fail: ${e.message}")
        }
        // 延迟执行，等进程起来
        // 唤醒 Termux 会把它拉到前台 —— 600ms 后把自己带回前台
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                val back = context.packageManager.getLaunchIntentForPackage(context.packageName)
                back?.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                )
                back?.let { context.startActivity(it) }
            }
        }, 600L)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runInBackground(context, command)
        }, delayMs)
        return true
    }

    /**
     * 在 Termux 后台执行命令。依次尝试三种启动方式，全部失败才返回 false。
     * 详细过程写入 Download/MiMoChat/bridge_log.txt。
     */
    fun runInBackground(context: Context, command: String, args: List<String> = emptyList()): Boolean {
        if (!isTermuxInstalled(context)) {
            writeLog(context, "Termux 未安装（getPackageInfo 失败）")
            return false
        }
        val outDir = sharedOutDir(context).absolutePath
        val wrapped = "mkdir -p '$outDir' && $command; echo EXIT_$? >> '$outDir/bridge_log.txt'"
        writeLog(context, "CMD: $wrapped")

        fun baseIntent(): Intent {
            return Intent(RUN_COMMAND_ACTION).apply {
                component = ComponentName(TERMUX_PKG, "com.termux.app.RunCommandService")
                putExtra(EXTRA_CMD, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_ARGS, arrayOf("-lc", wrapped))
                putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
                putExtra(EXTRA_BACKGROUND, true)
            }
        }

        // Android 8+ 必须用 startForegroundService：
        // startService 能返回 ComponentName（看起来成功）但服务不会真正启动。
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                context.startForegroundService(baseIntent())
                writeLog(context, "startForegroundService OK")
                return true
            } catch (e: Exception) {
                writeLog(context, "startForegroundService FAIL: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        try {
            context.startService(baseIntent())
            writeLog(context, "startService OK")
            return true
        } catch (e: Exception) {
            writeLog(context, "startService FAIL: ${e.javaClass.simpleName}: ${e.message}")
        }

        // 方法3：Activity + 同名 action（旧文档路径）
        try {
            val act = Intent(RUN_COMMAND_ACTION).apply {
                setPackage(TERMUX_PKG)
                putExtra(EXTRA_CMD, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_ARGS, arrayOf("-lc", wrapped))
                putExtra(EXTRA_BACKGROUND, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(act)
            writeLog(context, "startActivity OK")
            return true
        } catch (e: Exception) {
            writeLog(context, "startActivity FAIL: ${e.javaClass.simpleName}: ${e.message}")
        }
        return false
    }

    /** 聊天里的 /run 语法：/run <shell 命令>（自动去掉末尾中文标点） */
    fun parseChatCommand(text: String): String? {
        val t = text.trim()
        if (!t.startsWith("/run ")) return null
        var cmd = t.removePrefix("/run ").trim()
        // 防手滑：去掉末尾的中英文句读，避免污染文件名/参数
        cmd = cmd.trimEnd('、', '，', '。', '；', '！', '？', ',', '.', ';', '!', '?', ' ', '	')
        return cmd.ifBlank { null }
    }

    /** 环境自检文案 */
    fun diagnose(context: Context): String {
        val ok = isTermuxInstalled(context)
        return buildString {
            append(if (ok) "✓ Termux 已安装\n" else "✗ 未检测到 Termux\n")
            append("共享输出目录：${sharedOutDir(context).absolutePath}\n")
            append("（Termux 侧：~/storage/shared/Download/MiMoChat）\n")
            if (ok) {
                append("前置步骤：\n")
                append("1) Termux 设置 → 开启 Allow external apps\n")
                append("2) Termux 里执行 termux-setup-storage 并允许存储\n")
                append("试跑：/run echo ok > ~/storage/shared/Download/MiMoChat/test.txt")
            } else {
                append("可点下方按钮安装后再试。")
            }
        }
    }
}
