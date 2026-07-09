package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


/**
 * 拍卖行状态机枚举
 */
enum class AuctionState {
    IN_LIST,    // 处于列表页：准备点商品
    IN_DETAIL,  // 处于详情页：准备识价
    RECOVERY    // 异常/转场状态：等待或尝试返回
}

class AuctionManager(
    context: Context,
    private val ocrManager: OcrManager,
    private val screenCaptureManager: ScreenCaptureManager
) {
    private val TAG = "AuctionManager"

    // 业务内部状态
    private var lastPrice = -1L
    private var consecutiveCount = 0
    private var purchasedCount = 0

    // 从配置中读取
    private val config = ConfigManager.getAuctionConfig(context)

    /**
     * 核心逻辑入口：处理每一帧
     */
    suspend fun onFrame(bitmap: Bitmap): Boolean {
        // 0. 检查任务是否完成
        if (config.maxQuantity != 0L && purchasedCount >= config.maxQuantity) {
            return true // 返回 true 表示任务终结
        }

        // 1. 状态判定（真正 FSM 的核心）
        // 这里可以优化：不需要每一帧都全屏 OCR，可以根据上一次状态缩小识别区域
        val state = detectCurrentState(bitmap)

        when (state) {
            AuctionState.IN_LIST -> handleListState()
            AuctionState.IN_DETAIL -> handleDetailState(bitmap)
            AuctionState.RECOVERY -> {
                Log.d(TAG, "等待 UI 响应或处理弹窗...")
                delay(500)
            }
            else -> { /* 识别中，不做动作 */ }
        }

        return false
    }

    /**
     * 识别当前在哪个页面
     */
    private suspend fun detectCurrentState(bitmap: Bitmap): AuctionState {
        // 使用 OCR 识别标题关键字来判定页面
        val template = OpencvUtil.templateCache["button_retry"] ?: return AuctionState.RECOVERY

        // 1. 切换到 CPU 密集型线程池进行计算
        val resultPoint = withContext(Dispatchers.Default) {
            // 这里的 findImage 运行在后台，不会阻塞悬浮窗拖拽
            OpencvUtil.findImage(bitmap, template, 0.9)
        }
        // 2. 计算完成后，回到主线程处理结果（launch 默认在 Main）
        if (resultPoint != null) {
            return AuctionState.IN_DETAIL
        }else return AuctionState.IN_LIST
    }

    private suspend fun handleListState() {
        Log.d(TAG, "状态：列表页 -> 点击商品")
        AutomationService.instance?.click(GameConfig.Buttons.PaiMaiHang)
        lastPrice = -1L
        consecutiveCount = 0
        delay(600) // 等待详情页弹出的动画
    }

    private suspend fun handleDetailState(bitmap: Bitmap) {
        // 1. 识价
        val priceBitmap = screenCaptureManager.cropBitmap(GameConfig.Regions.MIN_PRICE, bitmap)
        val rawText = ocrManager.recognizeTextAsync(priceBitmap)
        priceBitmap.recycle()

        val price = extractPrice(rawText)
        val quantity = extractQuantity(rawText)

        // 2. 稳定性检查
        if (price > 0 && price == lastPrice) {
            consecutiveCount++
        } else {
            lastPrice = price
            consecutiveCount = 0
            return // 价格变动，等下一帧
        }

        if (consecutiveCount >= 3) {
            Log.e(TAG, "价格稳定: $price, 准备判断")

            // 3. 购买逻辑
            if (config.maxPrice == 0L || price <= config.maxPrice) {
                doPurchase(price, quantity)
            }

            // 4. 操作完必须返回
            AutomationService.instance?.click(GameConfig.Buttons.PaiMaiHang2)
            delay(800)
        }
    }

    private fun doPurchase(price: Long, qty: Long) {
        Log.e(TAG, "命中目标！执行购买: $price")
        // AutomationService.instance?.click(...)
        purchasedCount++
    }
}