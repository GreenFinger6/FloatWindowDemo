package com.example.floatwindowdemo.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AutomationService : AccessibilityService() {

    companion object {
        // 单例引用，方便在 FloatWindowService 中直接调用
        var instance: AutomationService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /**
     * 在屏幕指定坐标执行点击
     */
    fun click(x: Float, y: Float) {
        // 获取当前屏幕的绝对像素宽高
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // 自动转换逻辑：判断是否在 [0, 1] 范围内
        val finalX = if (x in 0f..1f) x * screenWidth else x
        val finalY = if (y in 0f..1f) y * screenHeight else y

        val path = Path()
        path.moveTo(finalX, finalY)

        // 构建点击手势
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                // 这里可以添加点击后的日志或回调
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                super.onCancelled(gestureDescription)
                // 如果你拖动时没静默，这里会疯狂报错
                android.util.Log.e("Automation", "点击被取消: 可能是由于物理触摸冲突")
            }
        }, null)
    }

    fun click(x: Int, y: Int) = click(x.toFloat(), y.toFloat())
    fun click(x: Double, y: Double) = click(x.toFloat(), y.toFloat())
    fun click(t: Pair<Float, Float>) = click(t.first, t.second)

    /**
     * 在屏幕上执行滑动/拖动
     * @param start 滑动起始坐标
     * @param end 滑动结束坐标
     * @param duration 滑动持续时间（毫秒），默认 500ms。时间越短速度越快。
     */
    suspend fun swipe(start: Pair<Float, Float>, end: Pair<Float, Float>, duration: Long = 500L): Boolean =
        suspendCancellableCoroutine { continuation ->

            val metrics = resources.displayMetrics
            val fStartX = if (start.first in 0f..1f) start.first * metrics.widthPixels else start.first
            val fStartY = if (start.second in 0f..1f) start.second * metrics.heightPixels else start.second
            val fEndX = if (end.first in 0f..1f) end.first * metrics.widthPixels else end.first
            val fEndY = if (end.second in 0f..1f) end.second * metrics.heightPixels else end.second

            val path = Path().apply {
                moveTo(fStartX, fStartY)
                lineTo(fEndX, fEndY)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)
        }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 必须重写，但如果不处理系统事件可以留空
    }

    override fun onInterrupt() {
        instance = null
    }
}