package com.mimochat.app.ui.screenqa

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
 * 屏幕问答悬浮窗（与 App 风格统一）：
 * - 进入后先不截屏，点「截屏」才截
 * - 透明度/大小用 + / - 调节
 * - 「全屏」进 App 内该次对话（没有则新建）；「退出」停掉悬浮球；「关闭」只关窗
 * - 可正常弹出输入法
 */
class FloatingQAWindow(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onSend: (question: String) -> Unit,
    private val onOpenFull: (question: String, answer: String) -> Unit,
    private val onExitBall: () -> Unit,
    private val onClose: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var answerView: TextView? = null
    private var inputView: EditText? = null
    private var statusView: TextView? = null

    private var alphaStep = 2 // 0..3 → 100% / 80% / 60% / 40%
    private var sizeStep = 1  // 0..2 → 小 / 中 / 大

    // App 风格
    private val accent = Color.parseColor("#2F6FED")
    private val bgCard = Color.parseColor("#FFFFFF")
    private val bgPage = Color.parseColor("#F4F6FB")
    private val ink = Color.parseColor("#1C1C1E")
    private val muted = Color.parseColor("#6E6E73")

    fun show() {
        if (root != null) return
        val view = buildView()
        root = view
        val lp = WindowManager.LayoutParams(
            widthForStep(),
            heightForStep(),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // 不加 FLAG_NOT_FOCUSABLE，否则输入法弹不出来
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(view, lp)
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
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun widthForStep() = when (sizeStep) { 0 -> dp(260); 2 -> dp(380); else -> dp(310) }
    private fun heightForStep() = when (sizeStep) { 0 -> dp(300); 2 -> dp(480); else -> dp(380) }
    private fun alphaValue() = when (alphaStep) { 0 -> 1.0f; 1 -> 0.8f; 2 -> 0.6f; else -> 0.4f }

    private fun pillBtn(text: String, filled: Boolean = true, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 12f
            minHeight = 0
            minWidth = 0
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(if (filled) Color.WHITE else accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (filled) accent else Color.TRANSPARENT)
                if (!filled) setStroke(dp(1), accent)
            }
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(bgPage)
                setStroke(dp(1), Color.parseColor("#E2E2E7"))
            }
            alpha = alphaValue()
            elevation = dp(10).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        // ---- 顶栏：标题 + 关闭 ----
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
        top.addView(pillBtn("关闭", filled = false) { onClose() })
        root.addView(top)

        // ---- 调节行：透明度-/+ 大小-/+ ----
        val tune = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun label(t: String) = TextView(context).apply {
            text = t
            textSize = 12f
            setTextColor(muted)
        }
        tune.addView(label("透明度"))
        tune.addView(pillBtn("－", filled = false) {
            if (alphaStep < 3) { alphaStep++; applyAlpha() }
        })
        tune.addView(pillBtn("＋", filled = false) {
            if (alphaStep > 0) { alphaStep--; applyAlpha() }
        })
        tune.addView(TextView(context).apply {
            text = "   "
        })
        tune.addView(label("大小"))
        tune.addView(pillBtn("－", filled = false) {
            if (sizeStep > 0) { sizeStep--; applySize() }
        })
        tune.addView(pillBtn("＋", filled = false) {
            if (sizeStep < 2) { sizeStep++; applySize() }
        })
        root.addView(tune, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // ---- 截屏按钮 ----
        root.addView(pillBtn("截屏") { onScreenshot() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })

        statusView = TextView(context).apply {
            text = "点「截屏」获取当前画面"
            textSize = 11f
            setTextColor(muted)
        }
        root.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        // ---- 输入 ----
        inputView = EditText(context).apply {
            hint = "输入问题，例如：是否给某某转了 500 元？直接回答是或否"
            setSingleLine(false)
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(ink)
            setHintTextColor(Color.parseColor("#9E9E9E"))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(bgCard)
                setStroke(dp(1), Color.parseColor("#E2E2E7"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isFocusable = true
            isFocusableInTouchMode = true
            setOnFocusChangeListener { v, has ->
                if (has) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        root.addView(inputView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(pillBtn("发送提问") {
            val q = inputView?.text?.toString()?.trim().orEmpty()
            if (q.isNotEmpty()) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(inputView?.windowToken, 0)
                onSend(q)
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // ---- 答案 ----
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ).apply { topMargin = dp(8) }
        }
        answerView = TextView(context).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            visibility = View.GONE
        }
        scroll.addView(answerView)
        root.addView(scroll)

        // ---- 底栏：全屏 / 退出 ----
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bottom.addView(pillBtn("全屏查看") {
            onOpenFull(
                inputView?.text?.toString()?.trim().orEmpty(),
                answerView?.text?.toString().orEmpty()
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(pillBtn("退出悬浮球", filled = false) { onExitBall() })
        root.addView(bottom, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // 拖动：仅顶栏可拖，避免输入区误拖
        makeDraggable(top)

        // 点击窗体时允许获得焦点（键盘）
        root.setOnClickListener { }
        return root
    }

    private fun applyAlpha() {
        root?.alpha = alphaValue()
    }

    private fun applySize() {
        val lp = root?.layoutParams as? WindowManager.LayoutParams ?: return
        lp.width = widthForStep()
        lp.height = heightForStep()
        wm.updateViewLayout(root, lp)
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
                    val lp = root?.layoutParams as? WindowManager.LayoutParams
                    if (lp != null) {
                        lp.x += (event.rawX - lastX).toInt()
                        lp.y += (event.rawY - lastY).toInt()
                        wm.updateViewLayout(root, lp)
                        lastX = event.rawX; lastY = event.rawY
                    }
                    true
                }
                else -> false
            }
        }
    }
}
