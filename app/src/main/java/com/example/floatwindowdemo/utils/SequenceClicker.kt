package com.example.floatwindowdemo.utils

import android.graphics.Bitmap
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 序列点击执行器
 * @param taskList 模板名称列表
 * @param onFinished 执行完毕后的可选回调
 */
class SequenceClicker(
    private val taskList: List<String>,
    private val onFinished: (() -> Unit)? = null // 默认为空，增强灵活性
) {
    private var currentIndex = 0
    private var consecutiveCount = 0
    private var retryCount = 0
    private val STABLE_REQUIRED = 3
    private val MAX_RETRY_PER_STEP = 100
    private val TAG = "SequenceClicker"

    /**
     * 点击序列模版
     * @return true 表示序列执行完毕（成功或超时）
     */
    suspend fun processFrame(bitmap: Bitmap): Boolean {
        if (currentIndex >= taskList.size) {
            onFinished?.invoke()
            return true
        }

        val target = taskList[currentIndex]
        val template = OpencvUtil.templateCache[target] ?: return true

        val location = withContext(Dispatchers.Default) {
            OpencvUtil.findImage(bitmap, template)
        }

        if (location != null) {
            consecutiveCount++
            if (consecutiveCount >= STABLE_REQUIRED) {
                // 执行点击
                Log.d(TAG, "点击${target}")
                AutomationService.instance?.click(location.x.toFloat(), location.y.toFloat())

                // 状态流转
                currentIndex++
                consecutiveCount = 0
                retryCount = 0
                delay(1500L) // 点击后的固定等待时间
            }
        } else {
            consecutiveCount = 0
            retryCount++
            if (retryCount >= MAX_RETRY_PER_STEP) {
                Log.e(TAG, "寻找${target}次数过多，强制结束序列")
                onFinished?.invoke() // 超时也触发回调，或可根据需求区分
                return true // 找不到目标，强制结束序列
            }
        }
        return false
    }
}