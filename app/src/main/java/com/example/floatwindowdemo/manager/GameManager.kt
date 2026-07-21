package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 游戏状态枚举
 */
enum class GameState {
    TOWN,       // 城镇：准备进图、换号
    BATTLE,     // 战斗中
    RECOVERY    // 异常恢复：处理弹窗或卡死
}

class GameManager(private val context: Context) {
    private val TAG = "GameManager"
    private var isTown = true // 是否在城镇
    private var takeNext = false // 是否可以再次挑战

    /**
     * 每一帧的入口
     */
    suspend fun onFrame(bitmap: Bitmap): Boolean {
        val state = detectCurrentState()
        when (state) {
            GameState.BATTLE -> autoBattle(bitmap)
            GameState.TOWN -> {
                if (autoBattle(bitmap))return true
            }
            else -> {}
        }
        return false
    }
    /**
     * 状态判定
     */
    private fun detectCurrentState(): GameState{
        if (isTown){
            return GameState.TOWN
        }else return GameState.BATTLE
    }

    /**
     * 处理战斗场景
     */
    private suspend fun autoBattle (bitmap: Bitmap) : Boolean{
        // 使用 try-finally 确保裁剪的 Bitmap 无论如何都会被回收
        val roi = cropBitmap(Dungeon.Regions.BATTLE_STAMINA, bitmap)
        val currentHasPickUp = try {
            val text = withContext(Dispatchers.Default) {
                OcrManager.recognizeTextAsync(roi)
            }
            if (text.contains("拾取道具")) {
                takeNext = true
                Log.d(TAG, "检测到拾取提示，标记战役结束")
            }
            text.contains("拾取道具")
        } finally {
            roi.recycle() // 必须回收！防止 Native 内存溢出
        }

        // 2. 核心图像识别 (按钮匹配)
        // 将 OpenCV 耗时匹配移至 Dispatchers.Default 执行
        return withContext(Dispatchers.Default) {
            val tplAgain = OpencvUtil.templateCache[Dungeon.stateTemplateList[0]]
            val tplBack = OpencvUtil.templateCache[Dungeon.stateTemplateList[1]]
            val tplConfirm = OpencvUtil.templateCache["button_confirm"]

            if (tplAgain == null || tplBack == null || tplConfirm == null) {
                Log.e(TAG, "状态模版缺失，跳过本帧")
                return@withContext false
            }

            val againLoc = OpencvUtil.findImage(bitmap, tplAgain)
            val backLoc = OpencvUtil.findImage(bitmap, tplBack)
            val confirmLoc = OpencvUtil.findImage(bitmap, tplConfirm)

            // 3. 业务决策逻辑 (分支流转)
            when {
                // 优先级 1: 全局确认弹窗（如网络断开或体力不足）
                confirmLoc != null -> {
                    AutomationService.instance?.click(confirmLoc.x.toFloat(), confirmLoc.y.toFloat())
                    false
                }

                // 优先级 2: 结算阶段处理 (曾经检测到拾取，且当前拾取框已消失)
                takeNext && !currentHasPickUp -> {
                    when {
                        againLoc != null -> {
                            Log.i(TAG, "点击：再次挑战")
                            AutomationService.instance?.click(againLoc.x.toFloat(), againLoc.y.toFloat())
                            takeNext = false // 重置状态
                            delay(500)      // 点击后稍微缓冲
                            false
                        }
                        backLoc != null -> {
                            Log.i(TAG, "点击：返回城镇")
                            AutomationService.instance?.click(backLoc.x.toFloat(), backLoc.y.toFloat())
                            takeNext = false
                            true // 告知 Service 任务切换回城镇模式
                        }
                        else -> {
                            // 虽已拾取但没出按钮，可能在翻牌，执行攻击动作保底
                            AutomationService.instance?.click(Dungeon.Buttons.Attack, 2000L)
                            false
                        }
                    }
                }

                // 优先级 3: 正常战斗/寻路阶段
                else -> {
                    // 调用挂起式长按，此时协程会挂起 2s，collect 会自动跳过期间的帧
                    AutomationService.instance?.click(Dungeon.Buttons.Attack, 2000L)
                    false
                }
            }
        }
    }
}