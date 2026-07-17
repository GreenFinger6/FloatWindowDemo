package com.example.floatwindowdemo.utils

import android.util.Log
import com.example.floatwindowdemo.manager.ScreenCaptureManager
import com.example.floatwindowdemo.service.AutomationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
     * 执行点击序列（阻塞/挂起直到结束）
     * @param taskList 模板名称列表
     * @return true 表示全部成功点完，false 表示中途超时或失败
     */
    suspend fun runSequence(taskList: List<String>): Boolean {
        // 使用 withTimeoutOrNull 防止整个脚本线程因为找不到图像而永久卡死
        return withTimeoutOrNull(TOTAL_TIMEOUT) {
            for (target in taskList) {
                var consecutiveCount = 0
                var retryCount = 0
                var isStepSuccess = false

                while (retryCount < MAX_RETRY_PER_STEP) {
                    // 1. 从单例流中截获最新的一帧
                    val bitmap = ScreenCaptureManager.frameFlow.first()

                    try {
                        val template = OpencvUtil.templateCache[target]
                        if (template == null) {
                            Log.e(TAG, "模板 $target 未在缓存中，请先预加载")
                            break
                        }

                        // 2. OpenCV 识别（切换到 Default 线程执行计算）
                        val location = withContext(Dispatchers.Default) {
                            OpencvUtil.findImage(bitmap, template)
                        }

                        if (location != null) {
                            consecutiveCount++
                            if (consecutiveCount >= STABLE_REQUIRED) {
                                // 3. 识别稳定，执行点击
                                Log.d(TAG, "执行点击 $target")
                                AutomationService.instance?.click(location.x.toFloat(), location.y.toFloat())

                                isStepSuccess = true
                                break // 跳出当前步骤的 While 循环
                            }
                        } else {
                            consecutiveCount = 0 // 匹配中断，重置稳定计数
                            retryCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "识别过程发生异常: ${e.message}")
                    } finally {
                        // 4. 关键：手动回收通过 first() 拿到的临时 Bitmap
                        bitmap.recycle()
                    }

                    // 识别间隔，避免过度消耗 CPU
                    delay(100L)
                }

                if (isStepSuccess) {
                    // 步骤完成，等待 UI 刷新
                    delay(STEP_DELAY)
                } else {
                    Log.e(TAG, "步骤 $target 识别次数过多，强制跳过或结束")
                    // 如果某一步失败了，可以根据业务需求决定是 return@withTimeoutOrNull false
                    // 还是继续尝试下一个。这里我们选择直接返回失败。
                    return@withTimeoutOrNull false
                }
            }

            Log.d(TAG, "所有序列任务已成功执行完毕")
            true
        } ?: run {
            Log.e(TAG, "序列执行超时！已自动释放控制权")
            false
        }
    }
}