package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
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
    private val UI_CD = 100L // UI延迟，ms

    // 业务内部状态
    private var minPrice = Long.MAX_VALUE // 最低识别价格
    private var purchasedQty = 0L // 已购买数量
    private var attemptBuyCount = 0// 尝试购买次数
    private var successBuyCount = 0// 成功购买次数
    private var lastPrice = 0L// 最后识别价格

    // 从配置中读取
    private val config = ConfigManager.getAuctionConfig(context)
    val targetPrice = config.maxPrice  //设置的目标单价
    val targetQty = config.maxQuantity //设置的目标数量
    val isGreedy = config.isGreedy //是否贪心
    private val miaoCode = ConfigManager.getMiaoCode(context) //喵提醒码
    /**
     * 核心逻辑入口：处理每一帧
     */
    suspend fun onFrame(bitmap: Bitmap): Boolean {
        // 逻辑终点: 购买数量达到预期
        if (targetQty != 0L && purchasedQty >= targetQty) {
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
                AutomationService.instance?.click(Auction.Buttons.Back)
                // 等待界面弹出
                delay(UI_CD)
            }
        }

        return false

    }

    /**
     * 识别当前页面状态
     */
    suspend fun detectCurrentState(bitmap: Bitmap): AuctionState = withContext(Dispatchers.Default) {
        // 并行启动两个识别任务
        val listMatch = async {
            OpencvUtil.findInFrame(bitmap, Auction.TPL_PURCHASE, 0.9)
        }
        val detailMatch = async {
            OpencvUtil.findInFrame(bitmap, Auction.TPL_DETAIL, 0.9)
        }

        // 3. 等待结果并决策
        // 优先判断列表，再判断详情
        return@withContext when {
            listMatch.await() != null -> AuctionState.IN_LIST
            detailMatch.await() != null -> AuctionState.IN_DETAIL
            else -> AuctionState.RECOVERY
        }
    }

    private suspend fun handleListState() {
        // 点击商品
        AutomationService.instance?.click(Auction.Buttons.Detail)
        // 等待界面弹出
        delay(UI_CD)
    }

    private suspend fun handleDetailState(bitmap: Bitmap) {
        // 识别价格、数量
        val priceBitmap = cropBitmap(Auction.Regions.MIN_PRICE, bitmap)
        val rawText = withContext(Dispatchers.Default) {
            OcrManager.recognizeTextAsync(priceBitmap)
        }
        priceBitmap.recycle()

        // 使用正则从 String 中提取信息
        val price = extractPrice(rawText)
        var quantity = extractQuantity(rawText)

        // 成功识别到价格
        if (price > 0 && quantity > 0) {
            Log.d(TAG,"当前价格: $price, 数量: $quantity，目标价格：$targetPrice")

            // 更新最低价格
            if (price < minPrice) minPrice = price
            // 贪心模式限制购买数量
            if (isGreedy) quantity = 1

            // 是否需要购买
            val isPriceOk = targetPrice == 0L || price <= targetPrice
            val isQtyOk = targetQty == 0L || purchasedQty <= targetQty
            if (isPriceOk && isQtyOk) {

                // 当总购买价格超过1w泰拉时考虑二次确认价格
                if(price * quantity >= 10000L){
                    val newBitmap = ScreenCaptureManager.frameFlow.first()
                    val newPriceBitmap = cropBitmap(Auction.Regions.MIN_PRICE, newBitmap)
                    val newRawText = withContext(Dispatchers.Default) {
                        OcrManager.recognizeTextAsync(newPriceBitmap)
                    }
                    newPriceBitmap.recycle()
                    newBitmap.recycle()
                    lastPrice = extractPrice(newRawText)
                    // 价格稳定，且出现两帧之后才确定
                    if (price != lastPrice) {
                        Log.e(TAG,"价格不一致，首次: $price, 第二次: $lastPrice")
                        // 此时以第二次识别价格为准并重新判断
                        if (targetQty == 0L || lastPrice <= targetPrice) doPurchase(lastPrice, quantity)
                    }else doPurchase(price, quantity)
                } else doPurchase(price, quantity)
            }
        }

        // 操作完后，返回商品列表
        AutomationService.instance?.click(Auction.Buttons.Back)
        // 等待界面弹出
        delay(UI_CD)
    }

    private suspend fun doPurchase(price: Long, qty: Long) {
        // 尝试购买
        attemptBuyCount++

        // 设置购买数量
        val remainQty = targetQty - purchasedQty
        Log.i(TAG, "尝试购买单价: $price, 数量: $qty 已购数：$purchasedQty, 最低价:$minPrice")

        // 构造极速点击任务序列
        val buyTasks = mutableListOf<ClickTask>()

        if (qty == 1L || remainQty == 1L) {
            // 直接购买
        } else if (targetQty == 0L || remainQty >= qty) {
            // 自动补全最大数量
            buyTasks.add(ClickTask(Auction.TPL_INPUT_NUM, Auction.Regions.INPUT_BTN, 0.7))
            buyTasks.add(ClickTask(Auction.TPL_INPUT_MAX, Auction.Regions.INPUT_MAX_BTN, 0.7))
        } else {
            // 手动输入模式 (使用旧的 runSequence 保证兼容性)
            SequenceClicker.runSequence(Auction.getNumberTemplates(remainQty), false)

            val frame = ScreenCaptureManager.frameFlow.first()
            try {
                if (OpencvUtil.findInFrame(frame, Auction.TPL_INPUT_CONFIRM) != null) {
                    AutomationService.instance?.click(Auction.Buttons.InputNum)
                }
            } finally {
                frame.recycle()
            }
        }

        // 添加核心抢拍按钮 (ROI 强力加速)
        buyTasks.add(ClickTask(Auction.TPL_BUY, Auction.Regions.BUY_BTN, 0.7))
        buyTasks.add(ClickTask(Auction.TPL_CONFIRM, Auction.Regions.CONFIRM_BTN, 0.7))

        // 执行极速购买
        if (SequenceClicker.runFastSequence(buyTasks)) {
            Log.i(TAG, "等待购买结果识别...")
            var successFound = false
            val maxRetries = 10

            repeat(maxRetries) { i ->
                if (successFound) return@repeat
                delay(UI_CD)
                val frame = ScreenCaptureManager.frameFlow.first()
                val successBitmap = cropBitmap(Auction.Regions.SUCCESS_BUY, frame)

                try {
                    val rawText = withContext(Dispatchers.Default) {
                        OcrManager.recognizeTextAsync(successBitmap)
                    }
                    val sPrice = extractPrice(rawText)
                    val sQty = extractQuantity(rawText)

                    if (sPrice > 0 && sQty > 0) {
                        Log.i(TAG, "购买成功识别成功(第${i + 1}帧): 总价: $sPrice, 数量: $sQty")
                        successBuyCount++
                        purchasedQty += sQty
                        successFound = true

                        val info = "购买成功总价:$sPrice, 数量:$sQty; 已购数：$purchasedQty, 最低价:$minPrice, 成功率: ${successBuyCount.toDouble() / attemptBuyCount}"
                        Log.i(TAG, info)
                        if (miaoCode != null) postMiao(miaoCode, info)
                    }
                } finally {
                    successBitmap.recycle()
                    frame.recycle()
                }
            }
            if (!successFound) {
                Log.w(TAG, "未能在预期时间内识别到购买成功信息")
            }
        }

        // 操作完后，返回商品列表
        AutomationService.instance?.click(Auction.Buttons.Back)
        delay(UI_CD)
    }
}
