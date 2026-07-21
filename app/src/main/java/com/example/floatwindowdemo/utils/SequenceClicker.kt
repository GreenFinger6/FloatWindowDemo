package com.example.floatwindowdemo.utils

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
    private const val MAX_RETRY_PER_STEP = 60   // 每步最大尝试帧数 (约 6-10 秒)
    private const val STEP_DELAY = 1200L        // 点击成功后的转场等待时间
    private const val TOTAL_TIMEOUT = 30000L    // 整个序列执行的绝对超时时间 (30秒)

    /**
     * 执行点击序列（增强版：点击直到目标消失才继续）
     * @param taskList 模板名称列表
     * @return true 表示全部成功点完，false 表示中途超时或失败
     */
    suspend fun runSequence(taskList: List<String>): Boolean {
        return withTimeoutOrNull(TOTAL_TIMEOUT) {
            for (target in taskList) {
                var isStepFinished = false
                var stepRetryCount = 0

                // 外部大循环：确保该步骤点击并导致 UI 发生变化（目标消失）
                while (!isStepFinished && stepRetryCount < MAX_RETRY_PER_STEP) {

                    // 1. 等待目标出现并稳定
                    val location = findStableTarget(target)

                    if (location != null) {
                        // 2. 执行点击
                        Log.d(TAG, "检测到 $target，执行点击并等待其消失...")
                        AutomationService.instance?.click(location.x.toFloat(), location.y.toFloat())

                        // 3. 核心机制：等待目标消失
                        // 给 UI 反应时间，每隔 300ms 检查一次，最多检查 5 次
                        var disappeared = false
                        for (i in 0 until 5) {
                            delay(400L) // 每次点击后给点缓冲
                            val frame = ScreenCaptureManager.frameFlow.first()
                            val stillThere = try {
                                val template = OpencvUtil.templateCache[target]
                                withContext(Dispatchers.Default) {
                                    if (template != null) OpencvUtil.findImage(frame, template) != null else false
                                }
                            } finally {
                                frame.recycle()
                            }

                            if (!stillThere) {
                                disappeared = true
                                break
                            } else {
                                // 补点一下，防止没点中
                                AutomationService.instance?.click(location.x.toFloat(), location.y.toFloat())
                            }
                        }
                        if (disappeared) {
                            isStepFinished = true
                        }
                    } else {
                        // 没找到目标
                        stepRetryCount++
                        delay(200L)
                    }
                }

                if (!isStepFinished) {
                    Log.e(TAG, "步骤 $target 超时或失败")
                    return@withTimeoutOrNull false
                }

                // 步骤间固定 CD，防止转场动画干扰
                delay(500L)
            }
            true
        } ?: false
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