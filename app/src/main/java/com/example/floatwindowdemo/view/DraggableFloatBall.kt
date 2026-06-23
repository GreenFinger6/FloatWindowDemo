package com.example.floatwindowdemo.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent

class DraggableFloatBall @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false

    var onDragListener: ((dx: Float, dy: Float) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                isDragging = false
                // 重要：调用 super 让 View 处于 "Pressed" 状态，
                // 但要返回 true 以便接收后续的 MOVE 事件
                super.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                    isDragging = true
                    onDragListener?.invoke(dx, dy)
                    lastX = event.rawX
                    lastY = event.rawY
                }
                return true // 拖动过程中，消费掉所有 MOVE 事件
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    // --- 情况 A：如果是拖动结束 ---
                    // 1. 不调用 super.onTouchEvent(event)，防止基类触发 onClick
                    // 2. 清除点击状态（可选）
                    isPressed = false
                    return true // 消费掉 UP 事件，不让点击发生
                } else {
                    // --- 情况 B：如果只是普通点击 ---
                    // 调用 super 触发标准的 onClick 回调
                    return super.onTouchEvent(event)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}