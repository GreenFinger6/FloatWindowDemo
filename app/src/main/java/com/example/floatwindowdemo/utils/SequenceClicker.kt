package com.example.floatwindowdemo.utils

import android.graphics.Bitmap
import android.util.Log
import com.example.floatwindowdemo.manager.ScreenCaptureManager
import com.example.floatwindowdemo.service.AutomationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opencv.core.Point

/**
 * 序列点击执行器 - 单例优化版
 * 功能：主动从 Flow 中获取最新帧，直到顺序点击完 taskList 中所有模版。
 */
object SequenceClicker {
    private const val TAG = "SequenceClicker"

    // 配置参数
    private const val STABLE_REQUIRED = 3       // 连续识别成功次数，保证点击准确
    private const val MAX_RETRY_PER_STEP = 10  // 每步最大尝试帧数
    private const val TOTAL_TIMEOUT = 100000L    // 整个序列执行的绝对超时时间 (100秒)


    /**
     * 执行点击序列（贪婪匹配 + 自动回退机制）
     * 策略：同时寻找 [当前目标] 和 [上一步目标]
     * 1. 优先点击 [当前目标]，成功后进入下一序号
     * 2. 若当前不见但 [上一步目标] 还在，点击上一步尝试重新触发进度
     */
    suspend fun runSequence(taskList: List<String>): Boolean {
        var index = 0
        // 移除外部 withTimeoutOrNull，改为由内部业务逻辑控制
        while (index < taskList.size) {
            val target = taskList[index]
            val prevTarget = if (index > 0) taskList[index - 1] else null

            Log.d(TAG, "当前寻找: $target")

            // 1. 获取最新帧
            val bitmap = ScreenCaptureManager.frameFlow.first()
            try {
                // 2. 并行（逻辑上）检测当前目标和上一步目标
                val currentLoc = findInFrame(bitmap, target)
                val prevLoc = if (prevTarget != null) findInFrame(bitmap, prevTarget) else null

                when {
                    // A. 贪婪匹配：当前目标出现了
                    currentLoc != null -> {
                        Log.d(TAG, "发现当前目标 $target，执行点击...")
                        AutomationService.instance?.click(currentLoc.x.toFloat(), currentLoc.y.toFloat())

                        // 确认点击生效（消失）
                        if (isStableDisappear(target)) {
                            Log.d(TAG, "步骤 $target 成功，准备进入下一步")
                            index++
                            delay(500L) // 步骤间冷却
                        }
                    }

                    // B. 回退重试：当前不见，但上一步还在
                    prevLoc != null -> {
                        Log.w(TAG, "当前 $target 不见，但上步 $prevTarget 仍在，尝试补点回退...")
                        AutomationService.instance?.click(prevLoc.x.toFloat(), prevLoc.y.toFloat())
                        delay(1000L) // 给 UI 一点反应时间，继续当前循环
                    }

                    // C. 都没发现：可能在转场、加载或彻底跑偏
                    else -> {
                        delay(500L)
                    }
                }
            } finally {
                bitmap.recycle()
            }

            // 这里可以加一个最大重试计数保护，防止死循环
            // 或者根据你的需求，如果不找完不罢休，就一直循环
        }
        return true
    }

    /**
     * 辅助：在单帧中快速查找目标
     */
    private suspend fun findInFrame(bitmap: Bitmap, targetName: String): Point? {
        val template = OpencvUtil.templateCache[targetName] ?: return null
        return withContext(Dispatchers.Default) {
            OpencvUtil.findImage(bitmap, template)
        }
    }

    /**
     * 辅助：判断特定目标当前是否在画面中
     */
    private suspend fun isTargetPresent(targetName: String): Boolean {
        val frame = ScreenCaptureManager.frameFlow.first()
        return try {
            val template = OpencvUtil.templateCache[targetName]
            withContext(Dispatchers.Default) {
                if (template != null) OpencvUtil.findImage(frame, template) != null else false
            }
        } finally {
            frame.recycle()
        }
    }

    /**
     * 私有辅助：寻找稳定的目标
     */
    private suspend fun findStableTarget(targetName: String): Point? {
        var consecutiveCount = 0
        val template = OpencvUtil.templateCache[targetName] ?: return null

        for (i in 0 until 20) { // 最多找 20 帧
            val bitmap = ScreenCaptureManager.frameFlow.first()
            val loc = try {
                withContext(Dispatchers.Default) {
                    OpencvUtil.findImage(bitmap, template)
                }
            } finally {
                bitmap.recycle()
            }

            if (loc != null) {
                consecutiveCount++
                if (consecutiveCount >= STABLE_REQUIRED) return loc
            } else {
                consecutiveCount = 0
            }
            delay(100L)
        }
        return null
    }

    /**
     * 私有辅助：判断是否确实消失
     */
    private suspend fun isStableDisappear(targetName: String): Boolean {
        var consecutiveCount = 0
        val template = OpencvUtil.templateCache[targetName] ?: return false

        for (i in 0 until 20) { // 最多找 20 帧
            val bitmap = ScreenCaptureManager.frameFlow.first()
            val loc = try {
                withContext(Dispatchers.Default) {
                    OpencvUtil.findImage(bitmap, template)
                }
            } finally {
                bitmap.recycle()
            }

            if (loc != null) {
                consecutiveCount = 0

            } else {
                consecutiveCount++
                if (consecutiveCount >= STABLE_REQUIRED) return true
            }
            delay(100L)
        }
        return false
    }

    /**
     * 等待图像出现的方法
     * @param templateName 模板名称
     * @param timeoutMillis 最大等待时间
     * @param checkInterval 检查间隔
     */
    suspend fun waitForImage(
        templateName: String,
        timeoutMillis: Long = 50000L, // 最多等待50秒加载
        checkInterval: Long = 500L    // 每隔0.5秒查一次
    ): Boolean {
        Log.d(TAG, "等待 UI 出现: $templateName")
        return withTimeoutOrNull(timeoutMillis) {
            var found = false
            while (!found) {
                // 1. 获取最新帧
                val frame = ScreenCaptureManager.frameFlow.first()
                try {
                    val template = OpencvUtil.templateCache[templateName]
                    if (template != null) {
                        // 2. 后台线程执行识别
                        val loc = withContext(Dispatchers.Default) {
                            OpencvUtil.findImage(frame, template)
                        }
                        if (loc != null) {
                            found = true
                        }
                    } else {
                        Log.e(TAG, "等待失败：模板 $templateName 未加载")
                        return@withTimeoutOrNull false
                    }
                } finally {
                    // 3. 必须回收
                    frame.recycle()
                }
                if (!found) delay(checkInterval)
            }
            true
        } ?: false
    }
}