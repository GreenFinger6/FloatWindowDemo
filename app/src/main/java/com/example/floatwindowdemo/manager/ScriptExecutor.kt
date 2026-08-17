package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatwindowdemo.utils.Auction
import com.example.floatwindowdemo.utils.ConfigManager
import com.example.floatwindowdemo.utils.Dungeon
import com.example.floatwindowdemo.utils.OpencvUtil
import com.example.floatwindowdemo.utils.SequenceClicker
import com.example.floatwindowdemo.utils.YoloUtil
import com.example.floatwindowdemo.utils.cropBitmap
import com.example.floatwindowdemo.utils.extractStamina
import kotlinx.coroutines.*
import java.util.*

class ScriptExecutor(
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit // 用于回调通知 Service 显示 Toast
) {
    private val TAG = "ScriptExecutor"
    // 协程作用域，绑定到主线程，才能更新UI
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var monitorJob: Job? = null // 用于管理定时监控协程
    private val handler = Handler(Looper.getMainLooper())


    // 脚本运行状态参数
    @Volatile
    var isPausedBySystem = false
    var isRunning = false // 开始状态标记
    var isPaused = false // 暂停状态标记
    var isWaitingSchedule = false // 是否正在等待定时


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
     * 启动定时监控协程
     */
    fun startScheduleMonitor() {
        if (monitorJob != null) return
        isRunning = true
        isWaitingSchedule = true
        monitorJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "定时监控协程已启动，等待目标时间")
            while (isActive && isRunning && isWaitingSchedule) {
                try {
                    val config = ConfigManager.getScheduleConfig(context)
                    if (config.isEnabled) {
                        val now = Calendar.getInstance()
                        val nowHour = now.get(Calendar.HOUR_OF_DAY)
                        val nowMin = now.get(Calendar.MINUTE)

                        if (nowHour == config.hour && nowMin == config.minute) {
                            Log.i(TAG, "定时时间到达，自动准备启动脚本...")
                            isWaitingSchedule = false

                            // 切换到主线程触发业务
                            withContext(Dispatchers.Main) {
                                // 关键：先重置运行标记，否则 runStreamingTask 会因为检测到 isRunning==true 而直接 return
                                isRunning = false

                                val taskIndex = ConfigManager.getMainTask(context)
                                when (taskIndex) {
                                    0 -> startAuction()
                                    1 -> startTask()
                                    2 -> saveScreen()
                                    else -> Log.w(TAG, "定时启动失败：未定义对应索引的任务")
                                }
                                onStatusUpdate("定时启动成功")
                            }

                            // 如果是“仅一次”模式，则关闭定时开关
                            if (!config.isRepeatDaily) {
                                ConfigManager.saveScheduleConfig(context, config.copy(isEnabled = false))
                                Log.d(TAG, "已关闭“仅一次”定时开关")
                            }
                            break // 任务已启动，退出监控循环
                        }
                    } else {
                        // 如果中途关闭了定时配置，则退出等待
                        isWaitingSchedule = false
                        isRunning = false
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "定时监控异常: ${e.message}")
                }
                delay(10000L) // 每 10 秒精确检查一次
            }
            monitorJob = null
        }
    }

    /**
     * 执行一系列点击任务
     */
    fun execute() {
        OpencvUtil.preloadTemplates(context, Dungeon.entryPastDungeon)
        runStreamingTask { bitmap ->
            // processFrame 会处理所有细节，我们只需要判断是否结束
            if (SequenceClicker.runSequence(Dungeon.entryPastDungeon)) {
                onStatusUpdate("任务序列已执行完毕")
                stop()
            }
        }
    }

    fun showAllText() {
        runStreamingTask { bitmap ->
            val priceBitmap = cropBitmap(Dungeon.Regions.BATTLE_STAMINA, bitmap)
            val text = withContext(Dispatchers.Default) {
                OcrManager.recognizeTextAsync(priceBitmap)
            }
            Log.d(TAG, "📝 识别内容: ${text.replace("\n", " ")}")
            Log.d(TAG, "📝 当前体力: ${extractStamina(text)}")
            priceBitmap.recycle()
        }
    }

    fun saveScreen(){
        runStreamingTask { bitmap ->
            // 保存图片
            OpencvUtil.saveDebugBitmap(context, bitmap)
            onStatusUpdate("截图已保存")
            stop()
        }
    }
    fun test(){
        OpencvUtil.preloadTemplates(context, Dungeon.allTemplates)
        val auction = GameManager(context)
        runStreamingTask { bitmap ->
            auction.secretShop()
            if (SequenceClicker.runSequence(Dungeon.decomposeBag)) {
                onStatusUpdate("任务完成")
                stop()
            }
        }
    }
    /**
     * 开始拍卖行抢拍
     */
    fun startAuction() {
        // 识别模版预加载
        OpencvUtil.preloadTemplates(context, Auction.allTemplates)
        val auction = AuctionManager(context)
        runStreamingTask { bitmap ->
            if (auction.onFrame(bitmap)) {
                onStatusUpdate("任务完成")
                stop()
            }
        }
    }

    /**
     * 自动过图
     */
    fun startTask() {
        // 识别模版预加载
        OpencvUtil.preloadTemplates(context, Dungeon.allTemplates)
        val dungeon = GameManager(context)
        runStreamingTask { bitmap ->
            if (dungeon.onFrame(bitmap)) {
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
        isWaitingSchedule = false
        ScreenCaptureManager.stopStreaming()
        handler.removeCallbacksAndMessages(null)
        
        // 取消所有正在运行的协程
        monitorJob?.cancel()
        monitorJob = null
        scope.coroutineContext.cancelChildren()
        
        YoloUtil.release() // 脚本停止时清理 C++ 层模型缓存
        OpencvUtil.releaseTemplates() // 释放模版缓存
    }
}
