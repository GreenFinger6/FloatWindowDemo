package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.ConfigManager
import com.example.floatwindowdemo.utils.Auction
import com.example.floatwindowdemo.utils.OpencvUtil
import com.example.floatwindowdemo.utils.YoloUtil
import com.example.floatwindowdemo.utils.cropBitmap
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

    fun saveScreen(){
        runStreamingTask { bitmap ->
            // 保存图片
            OpencvUtil.saveDebugBitmap(context, bitmap)
            onStatusUpdate("任务完成")
            stop()
        }
    }

    /**
     * 开始拍卖行抢拍
     */
    fun startAuction() {
        OpencvUtil.preloadTemplates(context, Auction.templateList)
        val auction = AuctionManager(context, ocrManager)
        runStreamingTask { bitmap ->
            if (auction.onFrame(bitmap)) {
                onStatusUpdate("任务完成")
                stop()
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
                Log.d("YOLO", "目标[$index]: 类别=${YoloUtil.getLabelName(res.label)}, 置信度=${res.prob}, 坐标=(${res.centerX}, ${res.centerY})")
            }
        }
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
        OpencvUtil.releaseTemplates() // 释放模版缓存
    }
}