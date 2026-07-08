package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.ConfigManager
import com.example.floatwindowdemo.utils.GameConfig
import com.example.floatwindowdemo.utils.OpencvUtil
import com.example.floatwindowdemo.utils.YoloUtil
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
    // 协程作用域，绑定到主线程，才能更新UI
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())


    // 脚本运行状态参数
    private var retryCount = 0 //重试次数
    private var consecutiveCount = 0 // 观察到帧计数
    private val templateCache = mutableMapOf<String, Bitmap>() //缓存 Map，存放预加载的模板
    var isRunning = false // 开始状态标记
    var isPaused = false // 暂停状态标记
    private var isProcessing = false // 全局的“锁”，防止逻辑重叠

    // 全局配置参数
    private val MAX_RETRY = 100 // 识别失败最大重复次数
    private val CLICK_CD = 1500L // 点击延迟，ms

    private val UI_CD = 500L // UI延迟，ms
    private val REQUIRED_STABILITY_COUNT = 3 // 重复识别到多少帧才点击，建议设为 3 次



    /**
     * 【模版方法】
     *  封装了：启动检查、内存回收、协程启动、暂停挂起、异常捕获、流程解锁
     */
    private fun runStreamingTask(action: suspend (Bitmap) -> Unit) {
        if (isRunning) return
        isRunning = true
        isProcessing = false // 初始化锁状态

        screenCaptureManager.startStreaming { bitmap, onTaskComplete ->
            // 1. 预检查：如果不运行或正在处理，直接销毁并跳过
            if (!isRunning || isProcessing) {
                bitmap.recycle()
                onTaskComplete()
                return@startStreaming
            }
            scope.launch {
                try {
                    isProcessing = true // 【上锁】

                    // 2. 暂停挂起检查
                    while (isPaused && isRunning) {
                        delay(500L)
                    }
                    if (!isRunning) return@launch

                    // 3. 执行自定义逻辑
                    action(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "执行出错: ${e.message}")
                } finally {
                    // 4. 【收尾工作】无论逻辑如何，统一回收与解锁
                    isProcessing = false // 【解锁】
                    bitmap.recycle()
                    onTaskComplete()
                }
            }
        }
    }

    fun execute(taskList: List<String>) {
        var currentIndex = 0 // 记录当前执行任务索引
        consecutiveCount = 0

        runStreamingTask { bitmap ->

            // 逻辑终点
            if (currentIndex >= taskList.size) {
                onStatusUpdate("任务完成")
                stop()
                return@runStreamingTask // 相当于退出本次 action
            }

            val targetWord = taskList[currentIndex]
            val location = withContext(Dispatchers.Default) {
                ocrManager.findTextLocationAsync(bitmap, targetWord)
            }

            if (location != null) {
                consecutiveCount++
                if (consecutiveCount >= REQUIRED_STABILITY_COUNT) {
                    // 只有连续观察到指定次数，才认为UI已稳定，执行操作
                    Log.d(TAG,"目标稳定，执行点击: $targetWord")
                    AutomationService.instance?.click(location.x.toFloat(), location.y.toFloat())
                    currentIndex++
                    consecutiveCount = 0
                    delay(CLICK_CD)
                }
            } else {
                consecutiveCount = 0
                retryCount++
                if (retryCount >= MAX_RETRY) {
                    Log.d(TAG,"重试次数过多，找不着$targetWord")
                    stop()
                }
            }
        }
    }

    fun showAllText() {
        runStreamingTask { bitmap ->
            val text = withContext(Dispatchers.Default) {
                ocrManager.recognizeTextAsync(bitmap)
            }
            Log.d(TAG, "📝 识别内容: ${text.replace("\n", " ")}")
        }
    }

    fun findTargetTemplate(){
        preloadTemplates(listOf("button_retry"))
        val targetTemplate = "button_retry"
        val template = templateCache[targetTemplate] // 从缓存取，极快
        runStreamingTask { bitmap ->
            // 【逻辑终点
            if (template == null) {
                Log.e("OpenCV", "未能在缓存中找到模板: $targetTemplate")
                return@runStreamingTask // 结束本次回调
            }
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
        }
    }

    /**
     * 开始拍卖行抢拍
     */
    fun startAuction() {
        retryCount = 0; // 连续未识别到的次数
        var testStep = 0 // 0: 准备点击商品, 1: 准备识别价格
        var lastPrice = -1L // 最后一次识别到的价格
        var count = 0 // 记录已购买数量
        val config = ConfigManager.getAuctionConfig(context)
        val targetPrice = config.maxPrice
        val targetQty = config.maxQuantity
        runStreamingTask { bitmap ->
            // 逻辑终点: 购买数量达到预期
            if (targetQty != 0L && count >= targetQty) {
                onStatusUpdate("任务完成")
                stop()
                return@runStreamingTask
            }

            if (testStep == 0) {
                // 步骤0：点击商品
                AutomationService.instance?.click(GameConfig.Buttons.PaiMaiHang)
                testStep = 1
                delay(UI_CD) // 等待界面弹出
            } else {
                // 步骤1：识别价格
                val priceBitmap = screenCaptureManager.cropBitmap(GameConfig.Regions.MIN_PRICE, bitmap)
                val rawText = withContext(Dispatchers.Default) {
                    ocrManager.recognizeTextAsync(priceBitmap)
                }
                priceBitmap.recycle()

                // 使用正则从 String 中提取信息
                val price = extractPrice(rawText)
                val quantity = extractQuantity(rawText)
                // 价格稳定，且出现多帧之后才确定
                if (price > 0 && price == lastPrice) {
                    consecutiveCount++
                } else {
                    lastPrice = price
                    consecutiveCount = 0
                    retryCount++
                }
                // 连续识别到相同价格
                if (consecutiveCount >= REQUIRED_STABILITY_COUNT) {
                    // --- 价格已稳定，执行业务逻辑 ---
                    Log.e(TAG,"当前价格: $price, 数量: $quantity")

                    // 是否需要购买
                    val isPriceOk = targetPrice == 0L || price <= targetPrice
                    val isQtyOk = targetQty == 0L || quantity <= targetQty
                    if (isPriceOk && isQtyOk) {
                        Log.e(TAG,"尝试购买: $price, 数量: $quantity")
                        // todo 执行点击购买

                        // 喵提醒
                        val miaoCode = ConfigManager.getMiaoCode(context)
                        if (miaoCode != null) GameController.postMiao(miaoCode, "目标价格出现")

                        count++
                    }

                    // 操作完后，返回商品列表
                    AutomationService.instance?.click(GameConfig.Buttons.PaiMaiHang2)
                    delay(UI_CD)

                    // 重置步骤，进入下一次循环
                    testStep = 0
                    lastPrice = -1L
                    consecutiveCount = 0
                    retryCount = 0
                }else if (retryCount >= REQUIRED_STABILITY_COUNT) {
                    // 此时可能是商品没货，需要返回刷新
                    AutomationService.instance?.click(GameConfig.Buttons.PaiMaiHang2)
                    delay(UI_CD)
                    testStep = 0
                    retryCount = 0
                }
            }
        }
    }

    fun runYoloTask() {
        val initSuccess = YoloUtil.initModel(context.assets)
        Log.d(TAG, "YOLO模型初始化: $initSuccess")

        if (!initSuccess){
            stop()
            return
        }

        runStreamingTask { bitmap ->
            // 1. 调用 YOLO 识别
            // 建议在 Dispatchers.Default 中运行，因为 C++ 计算不占用协程挂起，但会占用 CPU
            val results = withContext(Dispatchers.Default) {
                YoloUtil.detect(bitmap)
            }

            // 2. 处理结果
            Log.d("YOLO", "识别到目标数量: ${results?.size}")
            results?.forEachIndexed { index, res ->
                Log.d("YOLO", "目标[$index]: 类别=${YoloUtil.getLabelName(res.label)}, 置信度=${res.score}, 坐标=(${res.x}, ${res.y})")
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
        YoloUtil.release() // 脚本停止时清理 C++ 层模型缓存
        releaseTemplates()
    }
}