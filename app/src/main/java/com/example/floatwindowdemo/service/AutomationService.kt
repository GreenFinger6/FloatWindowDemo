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
    suspend fun click(x: Float, y: Float, duration: Long = 50L): Boolean =
        suspendCancellableCoroutine { continuation ->
        // 获取当前屏幕的绝对像素宽高
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // 自动转换逻辑：判断是否在 [0, 1] 范围内
        val finalX = if (x in 0f..1f) x * screenWidth else x
        val finalY = if (y in 0f..1f) y * screenHeight else y

        val path = Path().apply {
            moveTo(finalX, finalY)
            lineTo(finalX, finalY)
        }

        // 构建点击手势
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(true)
                // 这里可以添加点击后的日志或回调
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (continuation.isActive) continuation.resume(false)
                // 如果你拖动时没静默，这里会疯狂报错
                android.util.Log.e("Automation", "点击被取消: 可能是由于物理触摸冲突")
            }
        }, null)
    }

    suspend fun click(t: Pair<Float, Float>, duration: Long = 50L) = click(t.first, t.second, duration)

    /**
     * 执行双击操作
     * @param interval 两次点击之间的间隔时间，通常 100ms-200ms 是双击的理想区间
     */
    suspend fun doubleClick(x: Float, y: Float, interval: Long = 150L): Boolean {
        // 执行第一次点击
        val firstClickSuccess = click(x, y, 50L)
        if (!firstClickSuccess) return false

        // 关键：等待一个短间隔，让系统识别为“双击”而不是两次独立的点击
        kotlinx.coroutines.delay(interval)

        // 执行第二次点击
        return click(x, y, 50L)
    }

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

    /**
    * 模拟按下系统返回键
    * @return 执行指令是否成功
    */

    fun performBack(): Boolean {
        // GLOBAL_ACTION_BACK 是无障碍服务内置的全局动作常量
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        if (success) {
            android.util.Log.d("Automation", "执行系统返回操作成功")
        }
        return success
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 必须重写，但如果不处理系统事件可以留空
    }

    override fun onInterrupt() {
        instance = null
    }
}