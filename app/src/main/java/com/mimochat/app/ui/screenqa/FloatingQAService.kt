package com.mimochat.app.ui.screenqa

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mimochat.app.MainActivity
import com.mimochat.app.data.ApiMessage
import com.mimochat.app.data.ChatPart
import com.mimochat.app.data.MiMoApi
import com.mimochat.app.data.ScreenQARepository
import com.mimochat.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 屏幕问答悬浮球服务。
 *
 * 手动模式：点球 → 打开悬浮问答窗 → 窗内点「截屏」→ 输入问题 → 单次问答
 * 自动模式：点球 → 后台截屏 → 按方案提问 → 通知栏显示「思考中…」再推结果
 *
 * 生命周期：START_NOT_STICKY；onDestroy 必释放 projection，避免系统残留「屏幕共享」。
 */
class FloatingQAService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_qa"
        const val NOTIF_FOREGROUND = 1001
        const val NOTIF_ANSWER = 1002
        const val EXTRA_PROJECTION_DATA = "projection_data"
        var projectionData: Intent? = null

        fun start(ctx: Context) {
            val i = Intent(ctx, FloatingQAService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, FloatingQAService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: FloatingBallView? = null
    private var qaWindow: FloatingQAWindow? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private val qaRepo by lazy { ScreenQARepository(applicationContext) }
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }
    private val api by lazy { MiMoApi() }
    private val app get() = application as com.mimochat.app.MiMoApplication
    private val chatRepo get() = app.chatRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat()
        showFloatingBall()
        setupProjection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)?.let {
            projectionData = it
            setupProjection()
        }
        // 不自动重启：异常被杀时宁可干净退出，也不要残留「屏幕共享」
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { qaWindow?.dismiss() }
        qaWindow = null
        floatingView?.let { runCatching { windowManager?.removeView(it) } }
        floatingView = null
        releaseProjection()
        scope.cancel()
    }

    private fun releaseProjection() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        projectionData = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "屏幕问答", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "屏幕问答状态与结果" }
            )
        }
    }

    private fun startForegroundCompat() {
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕问答运行中")
            .setContentText("点悬浮球开始提问 · 长按球退出")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_FOREGROUND, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else startForeground(NOTIF_FOREGROUND, notif)
    }

    private fun showFloatingBall() {
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WindowManager::class.java)
        val view = FloatingBallView(
            context = this,
            onClick = { onBallClick() },
            onLongClick = { stopSelf() }
        )
        floatingView = view
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20; y = 220
        }
        runCatching { windowManager?.addView(view, params) }
    }

    private fun setupProjection() {
        val data = projectionData ?: return
        if (mediaProjection != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val resultCode = data.getIntExtra("code", -1)
        @Suppress("DEPRECATION")
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mediaProjection = null
                virtualDisplay?.release()
                virtualDisplay = null
            }
        }, handler)
    }

    // ---------- 点球 ----------
    private fun onBallClick() {
        try {
            if (mediaProjection == null) {
                Toast.makeText(this, "截屏授权已失效，请到设置里重新启动悬浮球", Toast.LENGTH_LONG).show()
                return
            }
            scope.launch {
                val cfg = try { qaRepo.config.first() } catch (e: Exception) {
                    com.mimochat.app.data.ScreenQAConfig()
                }
                handler.post {
                    try {
                        if (cfg.autoMode && cfg.plans.isNotEmpty()) {
                            val plan = cfg.plans.firstOrNull { it.id == cfg.activePlanId }
                                ?: cfg.plans.first()
                            Toast.makeText(this@FloatingQAService, "屏幕问答：截屏中…", Toast.LENGTH_SHORT).show()
                            updateAnswerNotification("思考中…", ongoing = true)
                            captureScreen { bmp ->
                                if (bmp == null) {
                                    Toast.makeText(this@FloatingQAService, "截屏失败", Toast.LENGTH_SHORT).show()
                                    handler.post { updateAnswerNotification("截屏失败", ongoing = false) }
                                    return@captureScreen
                                }
                                autoAnswer(bmp, plan.prompt, plan.quality, plan.model)
                            }
                        } else if (cfg.autoMode && cfg.plans.isEmpty()) {
                            Toast.makeText(
                                this@FloatingQAService,
                                "自动模式已开，但还没有方案，请到设置里新建",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            openQaWindow()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FloatingQAService", "ball click failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingQAService", "onBallClick failed", e)
        }
    }

    // ---------- 手动模式悬浮窗 ----------
    private fun openQaWindow() {
        if (qaWindow != null) return
        if (windowManager == null) {
            windowManager = getSystemService(WindowManager::class.java)
        }
        val win = FloatingQAWindow(
            context = this,
            onScreenshot = {
                qaWindow?.setShotStatus("截屏中…")
                captureScreen { bmp ->
                    if (bmp == null) {
                        handler.post { qaWindow?.setShotStatus("截屏失败，请重试") }
                        return@captureScreen
                    }
                    val path = saveBitmap(bmp, 85)
                    lastShotPath = path
                    handler.post {
                        qaWindow?.setShotStatus("已截屏，可输入问题发送")
                    }
                }
            },
            onSend = { q ->
                val path = lastShotPath
                if (path == null) {
                    handler.post { qaWindow?.setShotStatus("请先点「截屏」") }
                    return@FloatingQAWindow
                }
                handler.post { qaWindow?.updateAnswer("思考中…") }
                scope.launch {
                    val cfg = qaRepo.config.first()
                    val plan = cfg.plans.firstOrNull { it.id == cfg.activePlanId }
                    val model = plan?.model ?: ""
                    val bytes = File(path).readBytes()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val url = "data:image/jpeg;base64,$b64"
                    val settings = settingsRepo.settings.first().let {
                        if (model.isNotBlank()) it.copy(model = model) else it
                    }
                    try {
                        val result = api.streamChat(
                            settings,
                            listOf(
                                ApiMessage("user", listOf(ChatPart.Text(q), ChatPart.ImageUrl(url)))
                            ),
                            onContentDelta = {}, onReasoningDelta = {}
                        )
                        val answer = result.content.ifBlank { "（无回答）" }
                        lastQuestion = q
                        lastAnswer = answer
                        createQAConversation(q, answer, path, result.reasoning)
                        handler.post { qaWindow?.updateAnswer(answer) }
                    } catch (e: Exception) {
                        handler.post { qaWindow?.updateAnswer("出错了：${e.message}") }
                    }
                }
            },
            onOpenFull = { q, a ->
                // 进 App：带上截图，若本次已有对话则在会话列表能看到
                val i = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    lastShotPath?.let { p -> putExtra("screen_qa_path", p) }
                    putExtra("screen_qa_question", q)
                }
                startActivity(i)
                qaWindow?.dismiss()
                qaWindow = null
            },
            onExitBall = {
                qaWindow?.dismiss()
                qaWindow = null
                stopSelf()
            },
            onClose = {
                qaWindow?.dismiss()
                qaWindow = null
            }
        )
        qaWindow = win
        win.show()
    }

    private var lastShotPath: String? = null
    private var lastQuestion: String = ""
    private var lastAnswer: String = ""
    // 自动模式通知状态：防止流式回调把最终结果盖掉
    @Volatile private var qaStatusFinal = false
    private var lastStatusAt = 0L
    private var qaSession = 0L

    // ---------- 自动模式 ----------
    private fun autoAnswer(bmp: Bitmap, prompt: String, quality: Int, model: String = "") {
        val session = ++qaSession
        qaStatusFinal = false
        lastStatusAt = 0L
        scope.launch {
            try {
                val path = saveBitmap(bmp, quality)
                val bytes = File(path).readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val url = "data:image/jpeg;base64,$b64"
                val settings = settingsRepo.settings.first().let {
                    if (model.isNotBlank()) it.copy(model = model) else it
                }
                fun progress(msg: String) {
                    // 节流：500ms 内最多刷一次；final 之后绝不再改
                    if (qaStatusFinal || session != qaSession) return
                    val now = System.currentTimeMillis()
                    if (now - lastStatusAt < 500) return
                    lastStatusAt = now
                    handler.post {
                        if (!qaStatusFinal && session == qaSession) {
                            updateAnswerNotification(msg, ongoing = true)
                        }
                    }
                }
                val result = api.streamChat(
                    settings,
                    listOf(
                        ApiMessage("user", listOf(ChatPart.Text(prompt), ChatPart.ImageUrl(url)))
                    ),
                    onContentDelta = { progress("生成中…") },
                    onReasoningDelta = { progress("思考中…") }
                )
                if (session != qaSession) return@launch
                val answer = result.content.ifBlank { "（无回答）" }
                qaRepo.setLastAnswer(answer)
                qaStatusFinal = true
                handler.post {
                    Toast.makeText(this@FloatingQAService, "问答完成，见通知", Toast.LENGTH_SHORT).show()
                    updateAnswerNotification(answer, ongoing = false)
                }
            } catch (e: Exception) {
                if (session != qaSession) return@launch
                qaStatusFinal = true
                handler.post {
                    Toast.makeText(this@FloatingQAService, "问答失败：${e.message}", Toast.LENGTH_LONG).show()
                    updateAnswerNotification("出错了：${e.message}", ongoing = false)
                }
            }
        }
    }

    private fun updateAnswerNotification(text: String, ongoing: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // 无通知权限时至少 Toast 出结果
                handler.post {
                    Toast.makeText(this, text.take(120), Toast.LENGTH_LONG).show()
                }
                return
            }
        }
        val nm = getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕问答")
            .setContentText(text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(
                if (ongoing) android.R.drawable.stat_notify_sync
                else android.R.drawable.ic_dialog_info
            )
            .setContentIntent(open)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (ongoing) b.setProgress(0, 0, true)
        nm.notify(NOTIF_ANSWER, b.build())
    }

    private suspend fun createQAConversation(
        question: String,
        answer: String,
        imagePath: String,
        reasoning: String
    ): Long {
        val title = "屏幕问答-" + question.take(12).replace("\n", " ")
        val model = settingsRepo.settings.first().model
        val convId = chatRepo.createConversation(model)
        chatRepo.renameConversation(convId, title)
        val db = app.database
        val now = System.currentTimeMillis()
        val att = com.mimochat.app.data.ChatRepository.attachmentsToJson(
            listOf(
                com.mimochat.app.data.Attachment(
                    type = "image", path = imagePath,
                    name = File(imagePath).name
                )
            )
        )
        db.messageDao().insert(
            com.mimochat.app.data.ChatMessage(
                conversationId = convId, role = "user", content = question,
                attachmentsJson = att, createdAt = now
            )
        )
        db.messageDao().insert(
            com.mimochat.app.data.ChatMessage(
                conversationId = convId, role = "assistant", content = answer,
                reasoning = reasoning, createdAt = now + 1
            )
        )
        return convId
    }

    private fun saveBitmap(bmp: Bitmap, quality: Int): String {
        val dir = File(filesDir, "screen_qa").apply { mkdirs() }
        val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), bos)
        f.writeBytes(bos.toByteArray())
        return f.absolutePath
    }

    // ---------- 截屏 ----------
    @SuppressLint("WrongConstant")
    private fun captureScreen(onDone: (Bitmap?) -> Unit) {
        val projection = mediaProjection ?: run { onDone(null); return }
        try {
            val wm = getSystemService(WindowManager::class.java)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val w = (metrics.widthPixels / 2).coerceAtMost(720)
            val h = (metrics.heightPixels / 2).coerceAtMost(1600)

            imageReader?.close()
            val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            if (virtualDisplay == null) {
                virtualDisplay = projection.createVirtualDisplay(
                    "screen_qa", w, h, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface, null, handler
                )
            } else {
                runCatching { virtualDisplay!!.setSurface(reader.surface) }.onFailure {
                    runCatching { virtualDisplay?.release() }
                    virtualDisplay = projection.createVirtualDisplay(
                        "screen_qa", w, h, metrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.surface, null, handler
                    )
                }
            }

            handler.postDelayed({
                try {
                    val image = reader.acquireLatestImage()
                    if (image == null) {
                        onDone(null); return@postDelayed
                    }
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * w
                    val bmp = Bitmap.createBitmap(
                        w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
                    bmp.recycle()
                    image.close()
                    onDone(cropped)
                } catch (e: Exception) {
                    onDone(null)
                }
            }, 120)
        } catch (e: Exception) {
            onDone(null)
        }
    }
}
