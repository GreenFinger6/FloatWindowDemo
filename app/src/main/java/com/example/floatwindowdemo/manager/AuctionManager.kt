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

class AuctionManager(private val context: Context) {
    private val TAG = "AuctionManager"
    private val UI_CD = 500L // UI延迟，ms

    // 业务内部状态
    private var lastPrice = -1L // 最后一次识别到的价格
    private var consecutiveCount = 0 // 观察到帧计数
    private var purchasedCount = 0 // 已购买数量

    // 从配置中读取
    private val config = ConfigManager.getAuctionConfig(context)
    val targetPrice = config.maxPrice
    val targetQty = config.maxQuantity
    /**
     * 核心逻辑入口：处理每一帧
     */
    suspend fun onFrame(bitmap: Bitmap): Boolean {
        // 逻辑终点: 购买数量达到预期
        if (targetQty != 0L && purchasedCount >= targetPrice) {
            return true // 返回 true 表示任务终结
        }

        // 1. 状态判定（真正 FSM 的核心）
        val state = detectCurrentState(bitmap)
        Log.d(TAG,"当前状态: $state")
        when (state) {
            AuctionState.IN_LIST -> handleListState()
            AuctionState.IN_DETAIL -> handleDetailState(bitmap)
            AuctionState.RECOVERY -> {
                // 尝试返回
                AutomationService.instance?.click(Auction.Buttons.PaiMaiHang2)
                // 等待界面弹出
                delay(UI_CD)
            }
        }

        return false
    }

    /**
     * 识别当前在哪个页面
     */
    suspend fun detectCurrentState(bitmap: Bitmap): AuctionState {
        // 使用 OCR 识别标题关键字来判定页面
        val template1 = OpencvUtil.templateCache[Auction.templateList[0]]
        val template2 = OpencvUtil.templateCache[Auction.templateList[1]]


        if (template1 != null && template2 != null) {
            // 切换到 CPU 密集型线程池进行计算
            val resultPoint1 = withContext(Dispatchers.Default) {
                OpencvUtil.findImage(bitmap, template1, 0.9)
            }
            if (resultPoint1 != null) {
                return AuctionState.IN_LIST
            }
            // 切换到 CPU 密集型线程池进行计算
            val resultPoint2 = withContext(Dispatchers.Default) {
                OpencvUtil.findImage(bitmap, template2, 0.9)
            }
            if (resultPoint2 != null) {
                return AuctionState.IN_DETAIL
            }

        }
        return AuctionState.RECOVERY
    }

    private suspend fun handleListState() {
        // 点击商品
        AutomationService.instance?.click(Auction.Buttons.PaiMaiHang)
        // 等待界面弹出
        delay(UI_CD)
    }

    private suspend fun handleDetailState(bitmap: Bitmap) {
        // 识别价格
        val priceBitmap = cropBitmap(Auction.Regions.MIN_PRICE, bitmap)
        val rawText = withContext(Dispatchers.Default) {
            OcrManager.recognizeTextAsync(priceBitmap)
        }
        priceBitmap.recycle()

        // 使用正则从 String 中提取信息
        val price = extractPrice(rawText)
        val quantity = extractQuantity(rawText)

        // 价格稳定，且出现多帧之后才确定
        if (price > 0 && price == lastPrice && quantity > 0) {
            consecutiveCount++
        } else {
            lastPrice = price
            consecutiveCount = 0
            return // 价格变动，等下一帧
        }

        if (consecutiveCount >= 1) {
            // --- 价格已稳定，执行业务逻辑 ---
            Log.d(TAG,"当前价格: $price, 数量: $quantity")

            // 是否需要购买
            val isPriceOk = targetPrice == 0L || price <= targetPrice
            val isQtyOk = targetQty == 0L || purchasedCount <= targetQty
            if (isPriceOk && isQtyOk) {
                doPurchase(price, quantity)
            }

            // 操作完后，返回商品列表
            AutomationService.instance?.click(Auction.Buttons.PaiMaiHang2)
            // 等待界面弹出
            delay(UI_CD)

            // 重置步骤，进入下一次循环
            lastPrice = -1L
            consecutiveCount = 0
        }
    }

    private suspend fun doPurchase(price: Long, qty: Long) {
        Log.e(TAG,"尝试购买: $price, 数量: $qty")
        // todo 执行点击购买

        // 喵提醒
        val miaoCode = ConfigManager.getMiaoCode(context)
        if (miaoCode != null) GameController.postMiao(miaoCode, "尝试购买:$price, 数量: $qty")

        purchasedCount++
    }
}