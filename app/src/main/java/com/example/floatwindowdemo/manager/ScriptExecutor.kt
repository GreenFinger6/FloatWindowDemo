package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.utils.Auction
import com.example.floatwindowdemo.utils.OpencvUtil
import com.example.floatwindowdemo.utils.SequenceClicker
import com.example.floatwindowdemo.utils.YoloUtil
import kotlinx.coroutines.*

class ScriptExecutor(
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit // 用于回调通知 Service 显示 Toast
) {
    private val TAG = "ScriptExecutor"
    // 协程作用域，绑定到主线程，才能更新UI
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())


    // 脚本运行状态参数
    @Volatile
    var isPausedBySystem = false
    var isRunning = false // 开始状态标记
    var isPaused = false // 暂停状态标记


    /**
     * 【模版方法】
     *  使用 Kotlin Flow 适配新的截图管理类
     */
    private fun runStreamingTask(action: suspend (Bitmap) -> Unit) {
        if (isRunning) return
        isRunning = true
        isPaused = false

        // 在协程中启动流的监听
        scope.launch{
            try {
                // 1. 开启物理截图流后台线程
                ScreenCaptureManager.startStreamingFlow(context)

                onStatusUpdate("脚本启动成功")

                // 2. 开始消费画面流
                // 由于 Channel 设置了 CONFLATED，这里 collect 拿到的永远是最新的 Bitmap
                ScreenCaptureManager.frameFlow.collect { bitmap ->

                    // 暂停挂起检查
                    while ((isPaused || isPausedBySystem) && isRunning) {
                        delay(500L)
                    }

                    // 如果外部调用了 stop()，立即退出 collect
                    if (!isRunning) {
                        bitmap.recycle()
                        return@collect
                    }

                    try {
                        // 3. 执行自定义业务逻辑
                        // 注意：如果逻辑中有 delay 或耗时操作，下一帧会在处理完后再来
                        action(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "逻辑执行异常: ${e.message}")
                    } finally {
                        // 4. 【极其重要】手动回收这一帧。
                        // 没进入 collect 的帧由 Channel 回收，进入这里的必须由消费者（我们）回收
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "画面流采集出错: ${e.message}")
                stop()
            } finally {
                // 确保流彻底关闭
                isRunning = false
                ScreenCaptureManager.stopStreaming()
            }
        }
    }


    /**
     * 执行一系列点击任务
     */
    fun execute(taskList: List<String>) {
        val clicker = SequenceClicker(taskList)
        runStreamingTask { bitmap ->
            // processFrame 会处理所有细节，我们只需要判断是否结束
            if (clicker.processFrame(bitmap)) {
                onStatusUpdate("任务序列已执行完毕")
                stop()
            }
        }
    }

    fun showAllText() {
        runStreamingTask { bitmap ->
            val text = withContext(Dispatchers.Default) {
                OcrManager.recognizeTextAsync(bitmap)
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
        val auction = AuctionManager(context)
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
        ScreenCaptureManager.stopStreaming()
        handler.removeCallbacksAndMessages(null)
        // 取消所有正在运行的协程任务
        scope.coroutineContext.cancelChildren()
        YoloUtil.release() // 脚本停止时清理 C++ 层模型缓存
        OpencvUtil.releaseTemplates() // 释放模版缓存
    }
}