package com.mimochat.app.ui.screenqa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 屏幕问答悬浮窗。
 *
 * 输入法：只让 EditText 原生处理焦点，不手动 showSoftInput、不随意 updateViewLayout，
 * 避免「键盘开关循环」。
 * 布局：透明度/大小各占一行，按钮不会挤出窗体。
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingQAWindow(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onSend: (question: String) -> Unit,
    private val onOpenFull: (question: String, answer: String) -> Unit,
    private val onExitBall: () -> Unit,
    private val onClose: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var windowView: LinearLayout? = null
    private var answerView: TextView? = null
    private var inputView: EditText? = null
    private var statusView: TextView? = null

    private var alphaStep = 2 // 0=100% 1=80% 2=60% 3=40%
    private var sizeStep = 1  // 0=小 1=中 2=大

    private val accent = Color.parseColor("#2F6FED")
    private val bgPage = Color.parseColor("#F4F6FB")
    private val bgCard = Color.parseColor("#FFFFFF")
    private val ink = Color.parseColor("#1C1C1E")
    private val muted = Color.parseColor("#6E6E73")
    private val border = Color.parseColor("#E2E2E7")

    fun show() {
        if (windowView != null) return
        val view = buildView()
        windowView = view
        try {
            val lp = WindowManager.LayoutParams(
                widthForStep(),
                heightForStep(),
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                // 可聚焦（要输入），但点窗外不拦
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                // PAN：键盘弹出时窗口上移，而不是 RESIZE 重排（重排会闪）
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            }
            wm.addView(view, lp)
        } catch (e: Exception) {
            android.util.Log.e("FloatingQAWindow", "show failed", e)
            windowView = null
        }
    }

    fun setShotStatus(text: String) {
        statusView?.post { statusView?.text = text }
    }

    fun updateAnswer(text: String) {
        answerView?.post {
            answerView?.text = text
            answerView?.visibility = View.VISIBLE
        }
    }

    fun dismiss() {
        windowView?.let { v ->
            runCatching {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                wm.removeView(v)
            }
        }
        windowView = null
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun widthForStep() = when (sizeStep) { 0 -> dp(280); 2 -> dp(360); else -> dp(310) }
    private fun heightForStep() = when (sizeStep) { 0 -> dp(320); 2 -> dp(520); else -> dp(400) }
    private fun alphaValue() = when (alphaStep) { 0 -> 1.0f; 1 -> 0.8f; 2 -> 0.6f; else -> 0.4f }

    private fun smallBtn(text: String, filled: Boolean, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 12f
            minHeight = 0
            minWidth = 0
            includeFontPadding = false
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextColor(if (filled) Color.WHITE else accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                if (filled) setColor(accent)
                else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), accent)
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun label(t: String) = TextView(context).apply {
        text = t
        textSize = 12f
        setTextColor(muted)
        setPadding(dp(2), 0, dp(4), 0)
    }

    /** 调节行：标题 + 两个按钮，一行放下 */
    private fun tuneRow(name: String, onMinus: () -> Unit, onPlus: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(smallBtn("－", false, onMinus))
            addView(TextView(context).apply { text = " " })
            addView(smallBtn("＋", false, onPlus))
        }
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(bgPage)
                setStroke(dp(1), border)
            }
            alpha = alphaValue()
            elevation = dp(10).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        // 顶栏（可拖动）
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(context).apply {
            text = "屏幕问答"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(smallBtn("关闭", false) { onClose() })
        root.addView(top)
        makeDraggable(top)

        // 调节两行 —— 各占一行，绝不挤出
        root.addView(tuneRow("透明度",
            onMinus = { if (alphaStep < 3) { alphaStep++; windowView?.alpha = alphaValue() } },
            onPlus = { if (alphaStep > 0) { alphaStep--; windowView?.alpha = alphaValue() } }
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        root.addView(tuneRow("大小",
            onMinus = { if (sizeStep > 0) { sizeStep--; applySize() } },
            onPlus = { if (sizeStep < 2) { sizeStep++; applySize() } }
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        // 截屏
        root.addView(smallBtn("截屏", true) { onScreenshot() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })

        statusView = TextView(context).apply {
            text = "点「截屏」获取当前画面"
            textSize = 11f
            setTextColor(muted)
        }
        root.addView(statusView)

        // 输入框：只用原生焦点，不手动弹键盘
        inputView = EditText(context).apply {
            hint = "输入问题，例如：是否给某某转了 500 元？"
            setSingleLine(false)
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(ink)
            setHintTextColor(Color.parseColor("#9E9E9E"))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(bgCard)
                setStroke(dp(1), border)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
        }
        root.addView(inputView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(smallBtn("发送提问", true) {
            val q = inputView?.text?.toString()?.trim().orEmpty()
            if (q.isNotEmpty()) {
                onSend(q)
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // 答案
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ).apply { topMargin = dp(8) }
            isFocusable = false
            isFocusableInTouchMode = false
        }
        answerView = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            visibility = View.GONE
        }
        scroll.addView(answerView)
        root.addView(scroll)

        // 底栏
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bottom.addView(smallBtn("全屏查看", true) {
            onOpenFull(
                inputView?.text?.toString()?.trim().orEmpty(),
                answerView?.text?.toString().orEmpty()
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(TextView(context).apply { text = " " })
        bottom.addView(smallBtn("退出悬浮球", false) { onExitBall() })
        root.addView(bottom, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        return root
    }

    private fun applySize() {
        val target = windowView ?: return
        val lp = target.layoutParams as? WindowManager.LayoutParams ?: return
        lp.width = widthForStep()
        lp.height = heightForStep()
        runCatching { wm.updateViewLayout(target, lp) }
    }

    private fun makeDraggable(handle: View) {
        var lastX = 0f
        var lastY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX; lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val target = windowView ?: return@setOnTouchListener true
                    val lp = target.layoutParams as? WindowManager.LayoutParams
                    if (lp != null) {
                        lp.x += (event.rawX - lastX).toInt()
                        lp.y += (event.rawY - lastY).toInt()
                        runCatching { wm.updateViewLayout(target, lp) }
                        lastX = event.rawX; lastY = event.rawY
                    }
                    true
                }
                else -> false
            }
        }
    }
}
