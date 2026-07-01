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
    // 创建一个协程作用域，绑定到主线程，才能更新UI
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    // 脚本运行状态参数
    private var currentIndex = 0 //当前index索引
    private var retryCount = 0 //重试次数
    private var consecutiveCount = 0 // 观察到帧计数
    private val templateCache = mutableMapOf<String, Bitmap>() //缓存 Map，存放预加载的模板

    // 配置参数
    private val MAX_RETRY = 100 // 识别失败最大重复次数
    private val CLICK_CD = 1500L // 点击延迟

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
                            location.x,
                            location.y
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
                    // 这一帧处理完了，无论成功失败，立即回收内存
                    bitmap.recycle()
                    // 通知截取下一帧
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
                    // 这一帧处理完了，无论成功失败，立即回收内存
                    bitmap.recycle()
                    // 通知截取下一帧
                    onTaskComplete()
                }
            }
        }
    }

    fun test(){
        preloadTemplates(listOf("button_retry"))
        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            val targetWord = "button_retry"
            val template = templateCache[targetWord] // 从缓存取，极快
            // 【解决办法】进行空检查
            if (template == null) {
                Log.e("OpenCV", "未能在缓存中找到模板: $targetWord")
                onTaskComplete() // 记得通知流继续，否则会卡死
                return@startStreaming // 结束本次回调
            }
            // 使用协程处理每一帧，避免卡顿悬浮窗拖拽
            scope.launch {
                try {
                    // 1. 切换到 CPU 密集型线程池进行计算
                    val resultPoint = withContext(Dispatchers.Default) {
                        // 这里的 findImage 运行在后台，不会阻塞悬浮窗拖拽
                        OpencvUtil.findImage(bitmap, template, 0.9)
                    }
                    // 2. 计算完成后，回到主线程处理结果（launch 默认在 Main）
                    if (resultPoint != null) {
                        Log.d("OpenCV", "测试匹配成功！中心坐标: x=${resultPoint.x}, y=${resultPoint.y}")
                        AutomationService.instance?.click(resultPoint.x, resultPoint.y)
                    } else {
                        Log.e("OpenCV", "测试匹配失败")
                    }
                } catch (e: Exception) {
                    // 处理可能的异常，防止崩溃
                    Log.e("Script", "本帧处理出错: ${e.message}")
                } finally {
                    // 测试保存图片
//                saveDebugBitmap(bitmap)
                    // 这一帧处理完了，无论成功失败，立即回收内存
                    bitmap.recycle()
                    // 通知截取下一帧
                    onTaskComplete()
                }
            }
        }
    }

    /**
     * 保存bitmap图片到应用缓存路径
     * @param bitmap 图片
     */
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


    /**
     * 从 Assets 文件夹读取图片并缓存
     * @param taskList 图片路径list
     */
    private fun preloadTemplates(taskList: List<String>) {
        // 先手动释放内存，再清空，最后重新加载
        releaseTemplates()
        taskList.forEach { taskName ->
            // 假设图片名和任务名一致
            val fileName = "templates/$taskName.png"
            val bitmap = loadBitmapFromAssets(context, fileName)
            if (bitmap != null) {
                templateCache[taskName] = bitmap
            }
        }
    }

    /**
     * 从 Assets 文件夹读取图片并转换为 Bitmap
     * @param fileName 相对于 assets目录的路径，例如 "templates/button_start.png"
     */
    fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap? {
        return try {
            context.assets.open(fileName).use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e("OpencvUtil", "无法读取 Assets 图片: $fileName, 错误: ${e.message}")
            null
        }
    }

    fun releaseTemplates() {
        // 1. 遍历 Map 中所有的 Bitmap 值，逐个调用 recycle() 释放 Native 内存
        templateCache.values.forEach { it.recycle() }

        // 2. 清空 Map，断开 Java 对象引用，让 Java GC 可以回收包装对象
        templateCache.clear()
    }

    fun stop() {
        isRunning = false
        screenCaptureManager.stopStreaming()
        handler.removeCallbacksAndMessages(null)
        // 取消所有正在运行的协程任务
        scope.coroutineContext.cancelChildren()
        releaseTemplates()
    }
}