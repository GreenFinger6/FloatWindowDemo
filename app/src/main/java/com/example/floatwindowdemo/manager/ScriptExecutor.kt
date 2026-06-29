package com.example.floatwindowdemo.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.OpencvUtil
import kotlinx.coroutines.*

class ScriptExecutor(
    private val screenCaptureManager: ScreenCaptureManager,
    private val ocrManager: OcrManager,
    private val onStatusUpdate: (String) -> Unit // 用于回调通知 Service 显示 Toast
) {
    // 创建一个协程作用域，绑定到主线程
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    // 脚本运行状态
    private var currentIndex = 0
    private var retryCount = 0
    private var consecutiveCount = 0

    // 配置参数
    private val MAX_RETRY = 100
    private val CLICK_CD = 1500L

    fun execute(taskList: List<String>) {
        if (isRunning) return
        isRunning = true
        currentIndex = 0
        retryCount = 0
        consecutiveCount = 0

        onStatusUpdate("脚本启动")

        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            if (!isRunning) return@startStreaming

            // 1. 检查任务是否完成
            if (currentIndex >= taskList.size) {
                onStatusUpdate("所有任务已完成")
                stop()
                return@startStreaming
            }

            val targetWord = taskList[currentIndex]

            // 2. 调用 OCR 识别
            ocrManager.findTextLocation(bitmap, targetWord,
                onFound = { x, y ->
                    retryCount = 0
                    consecutiveCount++

                    if (consecutiveCount >= 2) {
                        onStatusUpdate("执行点击: $x , $y ")

                        // 3. 执行点击
                        AutomationService.instance?.click(x, y) ?: onStatusUpdate("❌ 请开启无障碍服务")

                        consecutiveCount = 0
                        currentIndex++

                        // 4. 点击冷却
                        handler.postDelayed({
                            onTaskComplete()
                        }, CLICK_CD)
                    } else {
                        onTaskComplete()
                    }
                },
                onNotFound = {
                    consecutiveCount = 0
                    retryCount++

                    if (retryCount >= MAX_RETRY) {
                        onStatusUpdate("脚本卡住了：找不着 '$targetWord'")
                        stop()
                    } else {
                        onTaskComplete()
                    }
                }
            )
        }
    }

    fun showText(){
        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            scope.launch {
                try {
                    // 1. 异步执行 OpenCV 或 OCR
                    val text = withContext(Dispatchers.Default) {
                        ocrManager.recognizeTextAsync(bitmap)
                    }
                    val cleanText = text.replace("\n", " ")
                    Log.d("Script", "📝 [常规识别] -> $cleanText")
                } catch (e: Exception) {
                    // 处理可能的异常，防止崩溃
                    Log.e("Script", "本帧处理出错: ${e.message}")
                } finally {
                    // 【最关键的地方】
                    onTaskComplete()
                }
            }
        }
    }

    fun test(){
        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            // 使用协程处理每一帧，避免卡顿悬浮窗拖拽
            scope.launch {
                // 1. 切换到 CPU 密集型线程池进行计算
                val resultPoint = withContext(Dispatchers.Default) {
                    // 这里的 findImage 运行在后台，不会阻塞悬浮窗拖拽
                    OpencvUtil.findImage(bitmap, bitmap, 0.9)
                }

                // 2. 计算完成后，回到主线程处理结果（launch 默认在 Main）
                if (resultPoint != null) {
                    Log.d("OpenCV", "测试匹配成功！中心坐标: x=${resultPoint.x}, y=${resultPoint.y}")
                    onStatusUpdate("匹配成功: ${resultPoint.x}, ${resultPoint.y}")
                } else {
                    Log.e("OpenCV", "测试匹配失败")
                }
                // 3. 任务完成，通知捕获下一帧
                onTaskComplete()
            }
        }
    }

    fun stop() {
        isRunning = false
        screenCaptureManager.stopStreaming()
        handler.removeCallbacksAndMessages(null)
        // 取消所有正在运行的协程任务
        scope.coroutineContext.cancelChildren()
    }
}