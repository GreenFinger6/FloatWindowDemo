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
    fun click(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        // 构建点击手势：在 (x,y) 处点按 50 毫秒
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                // 点击成功后的逻辑
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