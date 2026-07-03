package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.GameConfig
import com.example.floatwindowdemo.utils.OpencvUtil
import com.example.floatwindowdemo.utils.extractPrice
import com.example.floatwindowdemo.utils.extractQuantity
import kotlinx.coroutines.*

class ScriptExecutor(
    private val context: Context,
    private val screenCaptureManager: ScreenCaptureManager,
    private val ocrManager: OcrManager,
    private val onStatusUpdate: (String) -> Unit // 用于回调通知 Service 显示 Toast
) {
    private val TAG = "ScriptExecutor"

    // 创建一个协程作用域，绑定到主线程，才能更新UI
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())


    // 脚本运行状态参数
    private var currentIndex = 0 //当前index索引
    private var retryCount = 0 //重试次数
    private var consecutiveCount = 0 // 观察到帧计数
    private val templateCache = mutableMapOf<String, Bitmap>() //缓存 Map，存放预加载的模板
    var isRunning = false // 开始状态标记
    var isPaused = false // 暂停状态标记

    // 配置参数
    private val MAX_RETRY = 100 // 识别失败最大重复次数
    private val CLICK_CD = 1500L // 点击延迟，1500ms
    private val REQUIRED_STABILITY_COUNT = 3 // 重复识别到多少帧才点击，建议设为 3 次

    fun execute(taskList: List<String>) {
        if (isRunning) return
        isRunning = true
        currentIndex = 0
        retryCount = 0
        consecutiveCount = 0


        // 在流开启后，使用 scope.launch 接管每一帧的逻辑
        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            // 检查是否点击停止
            if (!isRunning) {
                onTaskComplete()
                return@startStreaming
            }
            scope.launch {
                try {

                    // 如果处于暂停状态，就在这里循环等待，每 500ms 检查一次
                    while (isPaused && isRunning) {
                        delay(500L) // 挂起 500ms，不阻塞主线程，悬浮窗依然流畅
                    }

                    // 如果在暂停期间脚本被彻底停止了，直接退出
                    if (!isRunning) return@launch

                    // 1. 任务完成检查，逻辑终点
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
                        consecutiveCount++ // 观察到目标，计数加1
                        retryCount = 0 // 只要找到了，总重试/超时计数可以重置

                        if (consecutiveCount >= REQUIRED_STABILITY_COUNT) {
                            // 只有连续观察到指定次数，才认为UI已稳定，执行操作
                            Log.d(TAG,"目标稳定，执行点击: $targetWord")

                            AutomationService.instance?.click(
                                location.x.toFloat(),
                                location.y.toFloat()
                            )

                            currentIndex++ // 进入下一个任务
                            consecutiveCount = 0 // 重置连续计数，为下一个任务做准备
                            delay(CLICK_CD)
                        }
                    } else {
                        // 这一帧没找到目标
                        consecutiveCount = 0 // 连续中断，清零
                        Log.d(TAG,"未找到: $targetWord")
                        retryCount++
                        if (retryCount >= MAX_RETRY) {
                            Log.d(TAG,"脚本卡住了：找不着$targetWord")
                            stop()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "识别过程出错: ${e.message}")
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
                    Log.d(TAG, "📝 [常规识别] -> $cleanText")
                } catch (e: Exception) {
                    // 处理可能的异常，防止崩溃
                    Log.e(TAG, "本帧处理出错: ${e.message}")
                } finally {
                    // 这一帧处理完了，无论成功失败，立即回收内存
                    bitmap.recycle()
                    // 通知截取下一帧
                    onTaskComplete()
                }
            }
        }
    }

    fun showBitMap(){
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
//                    val resultPoint = withContext(Dispatchers.Default) {
//                        // 这里的 findImage 运行在后台，不会阻塞悬浮窗拖拽
//                        OpencvUtil.findImage(bitmap, template, 0.9)
//                    }
//                    // 2. 计算完成后，回到主线程处理结果（launch 默认在 Main）
//                    if (resultPoint != null) {
//                        Log.d("OpenCV", "测试匹配成功！中心坐标: x=${resultPoint.x}, y=${resultPoint.y}")
//                        AutomationService.instance?.click(resultPoint.x, resultPoint.y)
//                    } else {
//                        Log.e("OpenCV", "测试匹配失败")
//                    }
                    // 测试保存图片
                    saveDebugBitmap(bitmap)
                } catch (e: Exception) {
                    // 处理可能的异常，防止崩溃
                    Log.e(TAG, "本帧处理出错: ${e.message}")
                } finally {
                    // 这一帧处理完了，无论成功失败，立即回收内存
                    bitmap.recycle()
                    // 通知截取下一帧
                    onTaskComplete()
                }
            }
        }
    }

    // 拍卖行蹲价格
    fun test() {
        if (isRunning) return
        isRunning = true
        currentIndex = 0
        var testStep = 0 // 0: 准备点击商品, 1: 准备识别价格
        var lastPrice = -1L // 最后一次识别到的价格

        // 在流开启后，使用 scope.launch 接管每一帧的逻辑
        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            // 检查是否点击停止
            if (!isRunning ) {
                onTaskComplete()
                return@startStreaming
            }
            scope.launch {
                try {
                    // 如果处于暂停状态，就在这里循环等待，每 500ms 检查一次
                    while (isPaused && isRunning) {
                        delay(500L) // 挂起 500ms，不阻塞主线程，悬浮窗依然流畅
                    }

                    // 如果在暂停期间脚本被彻底停止了，直接退出
                    if (!isRunning) return@launch

                    // 任务完成检查，逻辑终点
                    if (currentIndex >= MAX_RETRY) {
                        onStatusUpdate("任务完成")
                        stop()
                        return@launch
                    }
                    if (testStep == 0) {
                        // 在列表页点击商品
                        AutomationService.instance?.click(GameConfig.Buttons.PaiMaiHang)
                        testStep = 1 // 切换到识别阶段

                        // 等 600ms，期间所有新进来的帧都会因为 isProcessing=true 被丢弃
                        delay(500L)

                    } else{
                        // 查看价格
                        val priceBitmap = screenCaptureManager.cropBitmap(GameConfig.Regions.MIN_PRICE, bitmap)

                        // 异步获取结果
                        val rawText = withContext(Dispatchers.Default) {
                            ocrManager.recognizeTextAsync(priceBitmap)
                        }

                        // 裁剪出的临时图片用完立即回收
                        priceBitmap.recycle()

                        // 使用正则从 String 中提取信息 (逻辑解耦)
                        val price = extractPrice(rawText)
                        val quantity = extractQuantity(rawText)

                        // --- 价格稳定性校验逻辑 ---
                        if (price > 0 && price == lastPrice) {
                            consecutiveCount++
                        } else {
                            lastPrice = price
                            consecutiveCount = 0
                        }

                        if (consecutiveCount >= REQUIRED_STABILITY_COUNT) {
                            // --- 价格已稳定，执行业务逻辑 ---
                            Log.e(TAG,"当前价格: $price, 数量: $quantity")

                            // 是否需要购买

                            // 操作完后，返回商品列表
                            AutomationService.instance?.click(GameConfig.Buttons.PaiMaiHang2)

                            // 重置所有状态，进入下一个循环
                            testStep = 0 // 切换到点击阶段
                            lastPrice = -1L
                            consecutiveCount = 0
                            currentIndex++ //进入下次任务
                            delay(500L) // 等待返回列表的动画
                        } else {
                            // 价格未稳定，继续留在本步骤识别下一帧 ---
                            delay(100L) // 给一点微小的间歇，防止 OCR 跑得太快占满 CPU
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "识别过程出错: ${e.message}")
                } finally {
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
    private suspend fun saveDebugBitmap(bitmap: Bitmap) {
        // 切换到 IO 线程并“挂起”等待
        withContext(Dispatchers.IO) {
            try {
                // data -> data -> com.example.floatwindowdemo -> cache -> debug_frame.png。
                val file = java.io.File(context.cacheDir, "debug_frame.png")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.d(TAG, "调试图片已保存至: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "保存图片失败: ${e.message}")
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

    /**
     * 切换暂停/恢复状态
     */
    fun togglePause() {
        isPaused = !isPaused
    }

    fun stop() {
        isRunning = false
        isPaused = false
        screenCaptureManager.stopStreaming()
        handler.removeCallbacksAndMessages(null)
        // 取消所有正在运行的协程任务
        scope.coroutineContext.cancelChildren()
        releaseTemplates()
    }
}