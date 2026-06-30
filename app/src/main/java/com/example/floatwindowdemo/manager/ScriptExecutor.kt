package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.OpencvUtil
import kotlinx.coroutines.*

class ScriptExecutor(
    private val context: Context,
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

        // 在流开启后，使用 scope.launch 接管每一帧的逻辑
        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            if (!isRunning) {
                onTaskComplete()
                return@startStreaming
            }
            scope.launch {
                try {
                    // 1. 任务完成检查
                    if (currentIndex >= taskList.size) {
                        onStatusUpdate("任务完成")
                        stop()
                        return@launch
                    }

                    val targetWord = taskList[currentIndex]

                    // 2. 异步获取结果
                    val location = withContext(Dispatchers.Default) {
                        ocrManager.findTextLocationAsync(bitmap, targetWord)
                    }

                    // 3. 逻辑处理
                    if (location != null) {
                        onStatusUpdate("找到文字: $targetWord")
                        AutomationService.instance?.click(
                            location.x.toFloat(),
                            location.y.toFloat()
                        )
                        currentIndex++
                        delay(CLICK_CD) // 延迟不会阻塞主线程，很安全
                    } else {
                        onStatusUpdate("未找到: $targetWord")
                        retryCount++
                        if (retryCount >= MAX_RETRY) {
                            onStatusUpdate("脚本卡住了：找不着 '$targetWord'")
                            stop()
                        }
                    }

                } catch (e: Exception) {
                    Log.e("Script", "识别过程出错: ${e.message}")
                } finally {
                    // 【关键】无论是否找到，无论是否报错，都要通知截取下一帧
                    onTaskComplete()
                }
            }
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
                // 测试保存图片
//                saveDebugBitmap(bitmap)
                // 3. 任务完成，通知捕获下一帧
                onTaskComplete()
            }
        }
    }

    private fun saveDebugBitmap(bitmap: Bitmap) {
        // 使用 IO 线程保存，不阻塞主线程
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // data -> data -> com.example.floatwindowdemo -> cache -> debug_frame.png。
                val file = java.io.File(context.cacheDir, "debug_frame.png")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // Log.d("Script", "调试图片已保存至: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("Script", "保存图片失败: ${e.message}")
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