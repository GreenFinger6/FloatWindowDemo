package com.example.floatwindowdemo.utils

import android.util.Log
import com.example.floatwindowdemo.manager.ScreenCaptureManager
import com.example.floatwindowdemo.service.AutomationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

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
    suspend fun runSequence(taskList: List<String>, isDisappear: Boolean = true): Boolean {
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
                val currentLoc = OpencvUtil.findInFrame(bitmap, target)
                val prevLoc = if (isDisappear && prevTarget != null) OpencvUtil.findInFrame(bitmap, prevTarget) else null

                when {
                    // A. 贪婪匹配：当前目标出现了
                    currentLoc != null -> {
                        Log.d(TAG, "发现当前目标 $target，执行点击...")
                        AutomationService.instance?.click(currentLoc.x.toFloat(), currentLoc.y.toFloat())

                        // 是否需要确认点击生效（消失）
                        if(isDisappear){
                            if (isStableDisappear(target)) {
                                Log.d(TAG, "步骤 $target 成功，准备进入下一步")
                                index++
                                delay(500L) // 步骤间冷却
                            }
                        }else{
                            // 快速点击模式
                            index++
                            delay(60L) // 步骤间冷却
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
                        delay(10L)
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
     * 极速序列点击（带超时保护）
     */
    suspend fun runFastSequence(taskList: List<ClickTask>, timeoutMillis: Long = 5000L): Boolean {
        var index = 0
        var isDone = false

        Log.d(TAG, "启动极速序列点击: ${taskList.map { it.templateName }}，超时设置: ${timeoutMillis}ms")

        // 使用 withTimeoutOrNull 包裹 collect 过程
        val success = withTimeoutOrNull(timeoutMillis.milliseconds) {
            ScreenCaptureManager.frameFlow.takeWhile { !isDone }.collect { bitmap ->
                try {
                    if (index >= taskList.size) {
                        isDone = true
                        return@collect
                    }

                    val task = taskList[index]
                    // 识别逻辑...
                    val currentLoc = if (task.region != null) {
                        OpencvUtil.findInRegion(bitmap, task.templateName, task.region, task.threshold)
                    } else {
                        OpencvUtil.findInFrame(bitmap, task.templateName, task.threshold)
                    }

                    if (currentLoc != null) {
                        Log.d(TAG, "发现 ${task.templateName}，立即点击")
                        AutomationService.instance?.click(currentLoc.x.toFloat(), currentLoc.y.toFloat())
                        index++

                        if (index >= taskList.size) {
                            isDone = true
                        } else {
                            delay(10L) // 极速模式下的微小冷却
                        }
                    }
                } finally {
                    bitmap.recycle() // 确保即使在超时取消时，当前帧也被回收
                }
            }
            // 如果 collect 正常结束（isDone = true），返回是否全部完成
            index >= taskList.size
        }

        val finalResult = success ?: false
        if (!finalResult) {
            Log.e(TAG, "序列点击失败或超时。当前点击: ${taskList[index].templateName}")
        }
        return finalResult
    }

    /**
     * 辅助：判断特定目标当前是否在画面中
     */
    private suspend fun isTargetPresent(targetName: String): Boolean {
        val frame = ScreenCaptureManager.frameFlow.first()
        return try {
            withContext(Dispatchers.Default) {
                OpencvUtil.findInFrame(frame, targetName) != null
            }
        } finally {
            frame.recycle()
        }
    }

    /**
     * 私有辅助：判断是否确实消失
     */
    private suspend fun isStableDisappear(targetName: String): Boolean {
        var consecutiveCount = 0

        for (i in 0 until 10) { // 最多找 10 帧
            val bitmap = ScreenCaptureManager.frameFlow.first()
            val loc = try {
                withContext(Dispatchers.Default) {
                    OpencvUtil.findInFrame(bitmap, targetName)
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
     * 等待图像出现的方法 (Reactive 模式)
     * @param templateName 模板名称
     * @param timeoutMillis 最大等待时间，为 0 代表无限等待
     * @param checkInterval 检查间隔，减少 CPU 占用
     */
    suspend fun waitForImage(
        templateName: String,
        timeoutMillis: Long = 0,
        checkInterval: Long = 200L
    ): Boolean {
        var found = false
        val job: suspend () -> Unit = {
            ScreenCaptureManager.frameFlow.takeWhile { !found }.collect { bitmap ->
                try {
                    val loc = OpencvUtil.findInFrame(bitmap, templateName)
                    if (loc != null) {
                        found = true
                    }
                } finally {
                    bitmap.recycle()
                }
                if (!found && checkInterval > 0) delay(checkInterval.milliseconds)
            }
        }

        return if (timeoutMillis > 0) {
            withTimeoutOrNull(timeoutMillis.milliseconds) {
                job()
                found
            } ?: false
        } else {
            job()
            found
        }
    }
}
