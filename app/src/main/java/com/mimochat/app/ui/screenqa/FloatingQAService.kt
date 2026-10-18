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
import android.net.Uri
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
import com.mimochat.app.R
import com.mimochat.app.data.MiMoApi
import com.mimochat.app.data.ApiMessage
import com.mimochat.app.data.ChatPart
import com.mimochat.app.data.ScreenQARepository
import com.mimochat.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import android.util.Base64

/**
 * 屏幕问答悬浮服务：
 * - 悬浮球常驻（TYPE_APPLICATION_OVERLAY）
 * - 点击：截图 → 手动模式跳聊天带图 / 自动模式后台问答 → 通知
 */
class FloatingQAService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_qa"
        const val NOTIF_CAPTURE = 1001
        const val NOTIF_ANSWER = 1002
        const val EXTRA_PROJECTION_DATA = "projection_data"
        var projectionData: Intent? = null

        fun start(ctx: Context) {
            val i = Intent(ctx, FloatingQAService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, FloatingQAService::class.java))
        }

        fun isRunning(ctx: Context): Boolean {
            // 简易判断：由 enabled 标志配合 Service 自身
            return projectionData != null
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: FloatingBallView? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private val qaRepo by lazy { ScreenQARepository(applicationContext) }
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }
    private val api by lazy { MiMoApi() }

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
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { runCatching { windowManager?.removeView(it) } }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        scope.cancel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "屏幕问答",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "屏幕问答结果通知" }
            )
        }
    }

    private fun startForegroundCompat() {
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕问答运行中")
            .setContentText("点击悬浮球进行截屏问答")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_CAPTURE, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_CAPTURE, notif)
        }
    }

    private fun showFloatingBall() {
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WindowManager::class.java)
        val view = FloatingBallView(this) { onBallClick() }
        floatingView = view
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
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

    // ---------- 点击悬浮球 ----------
    private fun onBallClick() {
        if (mediaProjection == null) {
            Toast.makeText(this, "请先在设置里开启截屏权限", Toast.LENGTH_SHORT).show()
            openApp()
            return
        }
        Toast.makeText(this, "截屏中…", Toast.LENGTH_SHORT).show()
        handler.post {
            captureScreen { bmp ->
                if (bmp == null) {
                    Toast.makeText(this, "截屏失败", Toast.LENGTH_SHORT).show()
                    return@captureScreen
                }
                handleCapture(bmp)
            }
        }
    }

    private fun handleCapture(bmp: Bitmap) {
        scope.launch {
            val cfgList = qaRepo.config.first()
            if (cfgList.autoMode && cfgList.plans.isNotEmpty()) {
                val plan = cfgList.plans.firstOrNull { it.id == cfgList.activePlanId }
                    ?: cfgList.plans.first()
                autoAnswer(bmp, plan.prompt, plan.quality)
            } else {
                saveAndOpenChat(bmp)
            }
        }
    }

    private fun saveAndOpenChat(bmp: Bitmap) {
        val path = saveBitmap(bmp, 90)
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("screen_qa_path", path)
        }
        startActivity(i)
    }

    private fun autoAnswer(bmp: Bitmap, prompt: String, quality: Int) {
        scope.launch {
            try {
                val path = saveBitmap(bmp, quality)
                val bytes = File(path).readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val url = "data:image/jpeg;base64,$b64"
                val settings = settingsRepo.settings.first()
                val result = api.streamChat(
                    settings,
                    listOf(
                        ApiMessage("user", listOf(ChatPart.Text(prompt), ChatPart.ImageUrl(url)))
                    ),
                    onContentDelta = {},
                    onReasoningDelta = {}
                )
                val answer = result.content.ifBlank { "（无回答）" }
                qaRepo.setLastAnswer(answer)
                showAnswerNotification(answer)
            } catch (e: Exception) {
                showAnswerNotification("出错了：${e.message}")
            }
        }
    }

    private fun showAnswerNotification(answer: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕问答结果")
            .setContentText(answer.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(answer))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIF_ANSWER, notif)
    }

    private fun saveBitmap(bmp: Bitmap, quality: Int): String {
        val dir = File(filesDir, "screen_qa").apply { mkdirs() }
        val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), bos)
        f.writeBytes(bos.toByteArray())
        return f.absolutePath
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
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
            val w = (metrics.widthPixels / 2).coerceAtMost(720)   // 降采样省流量
            val h = (metrics.heightPixels / 2).coerceAtMost(1600)

            imageReader?.close()
            val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            virtualDisplay?.release()
            virtualDisplay = projection.createVirtualDisplay(
                "screen_qa",
                w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )

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
                    virtualDisplay?.release()
                    virtualDisplay = null
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
