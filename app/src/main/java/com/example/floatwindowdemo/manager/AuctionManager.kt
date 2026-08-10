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
    private var minPrice = Long.MAX_VALUE // 最低识别价格
    private var consecutiveCount = 0 // 观察到帧计数
    private var purchasedQty = 0L // 已购买数量

    // 从配置中读取
    private val config = ConfigManager.getAuctionConfig(context)
    val targetPrice = config.maxPrice  //设置的目标单价
    val targetQty = config.maxQuantity //设置的目标数量
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
        val quantity = extractQuantity(rawText)

        // 成功识别到价格
        if (price > 0 && quantity > 0) {
            Log.d(TAG,"当前价格: $price, 数量: $quantity")

            // 更新最低价格
            if (price < minPrice) minPrice = price

            // 是否需要购买
            val isPriceOk = targetPrice == 0L || price <= targetPrice
            val isQtyOk = targetQty == 0L || purchasedQty <= targetQty
            if (isPriceOk && isQtyOk) {
                doPurchase(price, quantity)
            }
        }

        // 操作完后，返回商品列表
        AutomationService.instance?.click(Auction.Buttons.Back)
        // 等待界面弹出
        delay(UI_CD)
    }

    private suspend fun doPurchase(price: Long, qty: Long) {
        // 设置购买数量
        val tmpQty = targetQty-purchasedQty
        Log.i(TAG,"尝试购买单价: $price, 数量: $tmpQty" +
            "当前已购买数量：$purchasedQty, 目前出现最低单价:$minPrice")
        if (targetQty != 0L && tmpQty < qty){
            // 此时需要手动输入数量
            SequenceClicker.runSequence(Auction.getNumberTemplates(tmpQty), false)
        }else if(qty != 1L){
            // 此时点击最大数量输入
            SequenceClicker.runSequence(listOf(Auction.TPL_INPUT_NUM, Auction.TPL_INPUT_MAX), false)
        }

        // 处理可能的输入确认, 不知道什么原因无法通过模版识别点击准确位置，只能点击偏移坐标
        while(true){
            val bitmap = ScreenCaptureManager.frameFlow.first()
            if (OpencvUtil.findInFrame(bitmap,Auction.TPL_INPUT_CONFIRM) != null) {
                AutomationService.instance?.click(Auction.Buttons.InputNum)
                delay(UI_CD)
            }else break
        }

        // 执行点击购买
        if (SequenceClicker.runSequence(Auction.buyList, false)) {
            Log.i(TAG, "等待购买...")
            var successFound = false
            val maxRetries = 5

            repeat(maxRetries) {
                if (successFound) return@repeat

                delay(UI_CD) // 给UI一点反应时间
                val frame = ScreenCaptureManager.frameFlow.first()
                val successBitmap = cropBitmap(Auction.Regions.SUCCESS_BUY, frame)
                
                try {
                    val rawText = withContext(Dispatchers.Default) {
                        OcrManager.recognizeTextAsync(successBitmap)
                    }
                    
                    val sPrice = extractPrice(rawText)
                    val sQty = extractQuantity(rawText)
                    
                    if (sPrice > 0 && sQty > 0) {
                        Log.i(TAG, "购买成功: 总价: $sPrice, 数量: $sQty" +
                                "当前已购买数量：$purchasedQty, 目前出现最低单价:$minPrice")

                        purchasedQty += sQty
                        successFound = true
                        // 喵提醒
                        if (miaoCode != null) postMiao(miaoCode, "购买成功: 总价:$sPrice, 数量:$sQty \n" +
                                "当前已购买数量：$purchasedQty, 目前出现最低单价:$minPrice")
                    }
                } finally {
                    successBitmap.recycle()
                }
            }
            
            if (!successFound) {
                Log.w(TAG, "未能在预期时间内识别到购买成功信息")
                // 兜底逻辑：如果识别不到，但点击了确定，purchasedQty 不更新或按预期更新需谨慎
            }
        }

        // 操作完后，返回商品列表
        AutomationService.instance?.click(Auction.Buttons.Back)
    }
}