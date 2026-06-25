package com.example.floatwindowdemo.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService

class ScriptExecutor(
    private val screenCaptureManager: ScreenCaptureManager,
    private val ocrManager: OcrManager,
    private val onStatusUpdate: (String) -> Unit // 用于回调通知 Service 显示 Toast
) {
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
            ocrManager.recognizeText(bitmap,
                onResult = { text->
                    val cleanText = text.replace("\n", " ")
                    Log.d("OcrManager", "📝 [常规识别] -> $cleanText")
                    onTaskComplete()
                },
                onError = {
                    onTaskComplete()
                }
            )
        }
    }

    fun stop() {
        isRunning = false
        screenCaptureManager.stopStreaming()
        handler.removeCallbacksAndMessages(null)
    }
}