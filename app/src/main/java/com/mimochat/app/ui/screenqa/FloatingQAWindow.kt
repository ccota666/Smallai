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
 * 输入法稳定方案（为什么这么写）：
 * 1. 窗口默认 FLAG_NOT_FOCUSABLE —— 不抢系统焦点，也就不会出现「键盘弹了又收」循环
 * 2. 只在用户点输入框那一次去掉该 flag，然后 toggleSoftInput(SHOW_FORCED)
 * 3. softInputMode 用 ADJUST_NOTHING；窗口放在屏幕上半部，键盘不会盖住输入框
 * 4. 绝不在聚焦/键盘回调里 updateViewLayout（那是闪烁的元凶）
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
    private var imeOn = false

    private var alphaStep = 2
    private var sizeStep = 1

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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                // 放上半屏，键盘出来也盖不到输入框
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (56 * context.resources.displayMetrics.density).toInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
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
    private fun heightForStep() = when (sizeStep) { 0 -> dp(340); 2 -> dp(520); else -> dp(420) }
    private fun alphaValue() = when (alphaStep) { 0 -> 1.0f; 1 -> 0.8f; 2 -> 0.6f; else -> 0.4f }

    /** 让窗口可聚焦并弹出键盘 —— 只做一次 */
    private fun enableImeAndFocus() {
        if (imeOn) return
        imeOn = true
        val target = windowView ?: return
        val lp = target.layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        runCatching { wm.updateViewLayout(target, lp) }
    }

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
                if (filled) setColor(accent) else {
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
        }

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

        root.addView(tuneRow("透明度",
            onMinus = { if (alphaStep < 3) { alphaStep++; windowView?.alpha = alphaValue() } },
            onPlus = { if (alphaStep > 0) { alphaStep--; windowView?.alpha = alphaValue() } }
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6) })

        root.addView(tuneRow("大小",
            onMinus = { if (sizeStep > 0) { sizeStep--; applySize() } },
            onPlus = { if (sizeStep < 2) { sizeStep++; applySize() } }
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4) })

        root.addView(smallBtn("截屏", true) { onScreenshot() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })

        statusView = TextView(context).apply {
            text = "点「截屏」获取当前画面"
            textSize = 11f
            setTextColor(muted)
        }
        root.addView(statusView)

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
            // 点输入框 → 开键盘（只此一处）
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    enableImeAndFocus()
                    v.requestFocus()
                    v.post {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                            as InputMethodManager
                        // SHOW_FORCED 对悬浮窗最稳
                        @Suppress("DEPRECATION")
                        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                    }
                }
                false
            }
        }
        root.addView(inputView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(smallBtn("发送提问", true) {
            val q = inputView?.text?.toString()?.trim().orEmpty()
            if (q.isNotEmpty()) onSend(q)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) })

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                .apply { topMargin = dp(8) }
        }
        answerView = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            visibility = View.GONE
        }
        scroll.addView(answerView)
        root.addView(scroll)

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
