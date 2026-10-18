package com.mimochat.app.ui.screenqa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** 可拖动的悬浮球，点击触发截屏问答 */
@SuppressLint("ViewConstructor")
class FloatingBallView(
    context: Context,
    private val onClick: () -> Unit,
    private val onLongClick: () -> Unit = {}
) : View(context) {
    private var longPressed = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2F6FED")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 36f
        isFakeBoldText = true
    }
    private val size = 140f // px
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressed = true
            onLongClick()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(size.toInt(), size.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val r = size / 2
        canvas.drawCircle(r, r, r - 4f, paint)
        canvas.drawText("问", r, r + 12f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                dragging = false
                longPressed = false
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, 700)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) {
                    removeCallbacks(longPressRunnable)
                }
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    val lp = layoutParams as android.view.WindowManager.LayoutParams
                    lp.x -= dx.toInt()
                    lp.y += dy.toInt()
                    (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                        .updateViewLayout(this, lp)
                }
                lastX = event.rawX
                lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (!dragging && !longPressed) {
                    runCatching { onClick() }
                }
                longPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
