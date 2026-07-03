package com.example.floatwindowdemo.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

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
        }, null)
    }

    fun click(x: Int, y: Int) = click(x.toFloat(), y.toFloat())
    fun click(x: Double, y: Double) = click(x.toFloat(), y.toFloat())
    fun click(t: Pair<Float, Float>) = click(t.first, t.second)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 必须重写，但如果不处理系统事件可以留空
    }

    override fun onInterrupt() {
        instance = null
    }
}