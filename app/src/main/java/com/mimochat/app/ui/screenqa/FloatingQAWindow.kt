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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 欧路词典式全屏半透明问答层：
 * - 整屏 MATCH_PARENT 覆盖层 + 半透明遮罩，内容卡片居上
 * - 全屏窗口可以正常走 ADJUST_RESIZE，输入法不再闪烁
 *   （小窗 + 输入法在国产 ROM 上普遍不稳，这是稳定做法）
 * - 点遮罩关闭；卡片内完成截屏/提问/全屏/退出
 */
@SuppressLint("ClickableViewAccessibility", "InflateParams")
class FloatingQAWindow(
    private val context: Context,
    private val onScreenshot: () -> Unit,
    private val onSend: (question: String) -> Unit,
    private val onOpenFull: (question: String, answer: String) -> Unit,
    private val onExitBall: () -> Unit,
    private val onClose: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var windowView: FrameLayout? = null
    private var cardView: LinearLayout? = null
    private var answerView: TextView? = null
    private var inputView: EditText? = null
    private var statusView: TextView? = null

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
        val root = buildRoot()
        windowView = root
        try {
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                // 全屏层可聚焦，输入法走标准 RESIZE 路径
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // ADJUST_NOTHING：键盘不重排窗口，从根上消灭闪烁
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            }
            wm.addView(root, lp)
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
    private fun cardWidth() = when (sizeStep) { 0 -> dp(300); 2 -> dp(380); else -> dp(340) }
    private fun cardMaxH() = when (sizeStep) { 0 -> dp(420); 2 -> dp(640); else -> dp(520) }
    private fun alphaValue() = when (alphaStep) { 0 -> 1.0f; 1 -> 0.92f; 2 -> 0.8f; else -> 0.65f }

    private fun smallBtn(text: String, filled: Boolean, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 12f
            minHeight = 0
            minWidth = 0
            includeFontPadding = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
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

    /** 全屏半透明遮罩 + 内容卡片 */
    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#66000000")) // 半透明黑遮罩
            // 点遮罩收起键盘 / 关窗
            setOnClickListener {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)
                onClose()
            }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(bgPage)
                setStroke(dp(1), border)
            }
            elevation = dp(12).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            alpha = alphaValue()
            // 点卡片不关窗
            setOnClickListener { }
        }
        cardView = card

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(context).apply {
            text = "屏幕问答"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(smallBtn("关闭", false) { onClose() })
        card.addView(top)

        card.addView(tuneRow("透明度",
            onMinus = { if (alphaStep < 3) { alphaStep++; cardView?.alpha = alphaValue() } },
            onPlus = { if (alphaStep > 0) { alphaStep--; cardView?.alpha = alphaValue() } }
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(6) })

        card.addView(tuneRow("大小",
            onMinus = { if (sizeStep > 0) { sizeStep--; applyCardSize() } },
            onPlus = { if (sizeStep < 2) { sizeStep++; applyCardSize() } }
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4) })

        card.addView(smallBtn("截屏", true) { onScreenshot() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })

        statusView = TextView(context).apply {
            text = "点「截屏」获取当前画面"
            textSize = 11f
            setTextColor(muted)
        }
        card.addView(statusView)

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
            // 只在用户点击时开一次键盘。
            // 千万不要用 focus 监听去 show/hide：键盘弹出会引发失焦→再聚焦→再弹，即闪烁循环
            var imeAsked = false
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.requestFocus()
                    if (!imeAsked) {
                        imeAsked = true
                        v.post {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                as InputMethodManager
                            @Suppress("DEPRECATION")
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                        }
                    }
                }
                false
            }
        }
        card.addView(inputView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        card.addView(smallBtn("发送提问", true) {
            val q = inputView?.text?.toString()?.trim().orEmpty()
            if (q.isNotEmpty()) onSend(q)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) })

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120)
            ).apply { topMargin = dp(8) }
        }
        answerView = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            visibility = View.GONE
        }
        scroll.addView(answerView)
        card.addView(scroll)

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
        card.addView(bottom, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        // 卡片放顶部偏上，键盘弹出不挡输入框
        val lp = FrameLayout.LayoutParams(cardWidth(), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(72)
        }
        root.addView(card, lp)
        return root
    }

    private fun applyCardSize() {
        val card = cardView ?: return
        val lp = card.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = cardWidth()
        card.layoutParams = lp
    }
}
