package com.mimochat.app.ui.screenqa

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * 非自动模式的悬浮问答窗：
 * 截屏后弹出，输入问题单次问答；答案可进全屏（App 内会话）或关闭。
 * 支持拖动、角点缩放、透明度调节。
 */
class FloatingQAWindow(
    private val context: Context,
    private val imagePath: String,
    private val onSend: (question: String) -> Unit,
    private val onOpenFull: (answer: String) -> Unit,
    private val onClose: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var answerView: TextView? = null
    private var inputView: EditText? = null
    private var alpha = 0.96f
    private var widthPx = (280 * context.resources.displayMetrics.density).toInt()
    private var heightPx = (340 * context.resources.displayMetrics.density).toInt()

    fun show() {
        val root = buildView()
        this.root = root
        val lp = WindowManager.LayoutParams(
            widthPx, heightPx,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(root, lp)
    }

    fun updateAnswer(text: String) {
        answerView?.post {
            answerView?.text = text
            answerView?.visibility = View.VISIBLE
        }
    }

    fun dismiss() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun buildView(): LinearLayout {
        val accent = Color.parseColor("#2F6FED")
        val bg = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#F7F8FC"))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            alpha = this@FloatingQAWindow.alpha
            elevation = dp(8).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        makeDraggable(root)

        // 顶栏
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "屏幕问答"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun barBtn(label: String, onClick: () -> Unit): Button {
            return Button(context).apply {
                text = label
                textSize = 11f
                minHeight = 0
                minWidth = 0
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(accent)
                }
                setOnClickListener { onClick() }
            }
        }
        top.addView(title)
        top.addView(barBtn("透明度") { cycleAlpha() })
        top.addView(barBtn("全屏") {
            val ans = answerView?.text?.toString().orEmpty()
            onOpenFull(ans)
        })
        top.addView(barBtn("关闭") { onClose() })
        root.addView(top)

        // 问题输入
        inputView = EditText(context).apply {
            hint = "输入问题…"
            setSingleLine(false)
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.parseColor("#1C1C1E"))
            setHintTextColor(Color.parseColor("#9E9E9E"))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.WHITE)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        root.addView(inputView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // 发送
        val send = barBtn("发送提问") {
            val q = inputView?.text?.toString()?.trim().orEmpty()
            if (q.isNotEmpty()) onSend(q)
        }
        root.addView(send, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // 答案
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ).apply { topMargin = dp(8) }
        }
        answerView = TextView(context).apply {
            text = "截屏已就绪，请输入问题后发送"
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            visibility = View.VISIBLE
        }
        scroll.addView(answerView)
        root.addView(scroll)

        // 角点缩放
        val corner = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#C0C4CC"))
            }
        }
        val lp = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        }
        // 用 FrameLayout 包一层太复杂，改为监听右下角：直接给 root 加 padding 手势
        // 简化：用 window 拖动手势在右下 24dp 内缩放
        addResizeHandle(root)

        return root
    }

    private fun cycleAlpha() {
        alpha = when {
            alpha > 0.9f -> 0.7f
            alpha > 0.6f -> 0.4f
            else -> 0.96f
        }
        root?.alpha = alpha
    }

    private fun makeDraggable(view: View) {
        var lastX = 0f
        var lastY = 0f
        var dragging = false
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastX = event.rawX; lastY = event.rawY; dragging = true
                v.parent?.requestDisallowInterceptTouchEvent(true)
                // 聚焦输入时允许键盘
                wm.updateViewLayout(root, (root!!.layoutParams as WindowManager.LayoutParams).apply {
                    flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                })
                true
            } else if (event.action == MotionEvent.ACTION_MOVE && dragging) {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (abs(dx) > 2 || abs(dy) > 2) {
                    val lp = root!!.layoutParams as WindowManager.LayoutParams
                    lp.x += dx.toInt()
                    lp.y += dy.toInt()
                    wm.updateViewLayout(root, lp)
                    lastX = event.rawX; lastY = event.rawY
                }
                true
            } else if (event.action == MotionEvent.ACTION_UP) {
                dragging = false
                true
            } else false
        }
    }

    private fun addResizeHandle(view: View) {
        // 右下角 28dp 区域：拖动缩放
        view.setOnTouchListener { v, event ->
            val w = v.width
            val h = v.height
            val inCorner = event.x > w - dp(28) && event.y > h - dp(28)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (inCorner) {
                        v.tag = floatArrayOf(event.rawX, event.rawY, widthPx.toFloat(), heightPx.toFloat())
                        true
                    } else {
                        makeDraggableTouch(v, event)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val tag = v.tag as? FloatArray
                    if (tag != null) {
                        val dx = event.rawX - tag[0]
                        val dy = event.rawY - tag[1]
                        widthPx = (tag[2] + dx).toInt().coerceIn(dp(220), dp(640))
                        heightPx = (tag[3] + dy).toInt().coerceIn(dp(180), dp(900))
                        val lp = root!!.layoutParams as WindowManager.LayoutParams
                        lp.width = widthPx
                        lp.height = heightPx
                        wm.updateViewLayout(root, lp)
                        true
                    } else makeDraggableTouch(v, event)
                }
                MotionEvent.ACTION_UP -> {
                    v.tag = null
                    true
                }
                else -> false
            }
        }
    }

    private var dragLast = floatArrayOf(0f, 0f)
    private fun makeDraggableTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragLast = floatArrayOf(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val lp = root!!.layoutParams as WindowManager.LayoutParams
                lp.x += (event.rawX - dragLast[0]).toInt()
                lp.y += (event.rawY - dragLast[1]).toInt()
                wm.updateViewLayout(root, lp)
                dragLast = floatArrayOf(event.rawX, event.rawY)
                return true
            }
        }
        return false
    }
}
