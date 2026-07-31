package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    private val UI_CD = 500L    // UI延迟，ms
    private var countHero = -1   // 当前角色下标
    private val roleList: List<RoleData> = Gson().fromJson(ConfigManager.getRoleDataJson(context), object : TypeToken<List<RoleData>>() {}.type)
    /**
     * 每一帧的入口
     */
    suspend fun onFrame(bitmap: Bitmap): Boolean {
        // 逻辑出口：所有角色任务执行完毕
        if(countHero > roleList.size) return true

        // 全局拦截器：优先处理确认弹窗（防止其阻塞所有逻辑）
        val tplConfirm = OpencvUtil.templateCache[Dungeon.TPL_CONFIRM]
        if (tplConfirm != null) {
            val confirmLoc = withContext(Dispatchers.Default) {
                OpencvUtil.findImage(bitmap, tplConfirm)
            }
            if (confirmLoc != null) {
                Log.d(TAG, "检测到确认弹窗，优先点击处理")
                AutomationService.instance?.click(confirmLoc.x.toFloat(), confirmLoc.y.toFloat())
                // 处理了弹窗后，本帧直接返回，不执行后续状态逻辑，等下一帧环境“干净”后再检测
                return false
            }
        }


        // 3. 状态分发逻辑
        val state = detectCurrentState()
        when (state) {
            GameState.BATTLE -> {
                if(autoBattle(bitmap)){ // 当前角色战斗结束
                    // 返回角色选择界面
                    backSelectHero()
                    // 切换状态
                    isTown = true
                }
            }
            GameState.TOWN -> {
                // 循环向后找启用的角色
                while (++countHero < roleList.size) {
                    if (roleList[countHero].isEnabled) {
                        // 选择下一个启用的角色进入城镇
                        switchHero(countHero)
                        // 进入深渊
                        SequenceClicker.runSequence(Dungeon.entryPastDungeon)
                        // 切换状态
                        isTown = false
                        return false
                    }
                }
                return true
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
            text.contains("道具")
        } finally {
            roi.recycle() // 必须回收！防止 Native 内存溢出
        }

        return withContext(Dispatchers.Default) {
            val tplAgain = OpencvUtil.templateCache[Dungeon.TPL_RE_CHALLENGE]
            val tplBack = OpencvUtil.templateCache[Dungeon.TPL_BACK_2_TOWN]


            if (tplAgain == null || tplBack == null) {
                Log.e(TAG, "状态模版缺失，跳过本帧")
                return@withContext false
            }

            val againLoc = OpencvUtil.findImage(bitmap, tplAgain)
            val backLoc = OpencvUtil.findImage(bitmap, tplBack)

            // 3. 业务决策逻辑 (分支流转)
            when {
                // 优先级 : 结算阶段处理 (曾经检测到拾取，且当前拾取框已消失)
                !currentHasPickUp -> {
                    when {
                        againLoc != null -> {
                            Log.i(TAG, "点击：再次挑战")
                            AutomationService.instance?.click(againLoc.x.toFloat(), againLoc.y.toFloat())
                            delay(UI_CD*4)      // 点击后稍微缓冲
                            false
                        }
                        backLoc != null -> {
                            Log.i(TAG, "点击：返回城镇")
                            AutomationService.instance?.click(backLoc.x.toFloat(), backLoc.y.toFloat())
                            true // 告知 Service 任务切换回城镇模式
                        }
                        else -> {
                            // 虽已拾取但没出按钮，可能在翻牌，执行攻击动作保底
                            AutomationService.instance?.click(Dungeon.Buttons.Attack, 2000L)
                            false
                        }
                    }
                }

                // 优先级 : 正常战斗/寻路阶段
                else -> {
                    // 调用挂起式长按，此时协程会挂起 2s，collect 会自动跳过期间的帧
                    AutomationService.instance?.click(Dungeon.Buttons.Attack, 2000L)
                    false
                }
            }
        }
    }

    /**
     * 切换到指定英雄
     */

    suspend fun switchHero(targetHero: Int): Boolean{
        var tmp = targetHero
        // 切换目标角色
        Log.d(TAG,"选择角色${tmp+1}")

        // 检测是否在角色选择界面
        if(!SequenceClicker.waitForImage(Dungeon.TPL_START_GAME))return false
        delay(UI_CD*10)

        // 开始向下滑动
        while (tmp >= 5){
            // 下移到下一栏角色
            AutomationService.instance?.swipe(Pair(623f, 508f), Pair(623f, 100f), 1000)
            tmp -= 5
        }
        delay(UI_CD*4)

        // 选择对应角色
        val heroButton = Dungeon.Buttons.SelectHeroList[tmp]
        AutomationService.instance?.click(heroButton.first,heroButton.second)

        // 开始游戏
        SequenceClicker.runSequence(listOf(Dungeon.TPL_START_GAME))

        // 检测是否出现委托
        return SequenceClicker.waitForImage(Dungeon.TPL_TASK_MENU)
    }

    /**
     * 返回角色选择 (深度优化版)
     * 逻辑：
     * 1. 强制回归城镇：只要不在城镇，就不断尝试寻找 TPL_BACK 并点击，直到看到 TPL_TASK_MENU。
     * 2. 循环打开设置：只要没看到“选择角色”按钮，就不断点击固定坐标的“设置”按钮。
     * 3. 序列点击：执行“选择角色”动作。
     * 4. 最终等待：无限等待直到进入“开始游戏”页面（角色选择界面）。
     */
    suspend fun backSelectHero(): Boolean {
        Log.d(TAG, "开始执行：返回角色选择流程 (无限重试模式)")

        // 循环打开设置菜单 ---
        while (true) {
            Log.d(TAG, "全局返回操作")

            // 尝试返回
            AutomationService.instance?.performBack()

            // 给 UI 反应时间
            delay(1200L)

            val frame = ScreenCaptureManager.frameFlow.first()
            try {
                val foundMenu = OpencvUtil.findInFrame(frame,Dungeon.TPL_SELECT_HERO)
                if (foundMenu != null) {
                    Log.d(TAG, "设置菜单已成功打开，检测到选择角色按钮")
                    break // 菜单已开，跳出循环
                }
            } finally {
                frame.recycle()
            }
            // 如果没跳出，会继续下一轮点击设置
        }

        // --- 第三阶段：执行选择角色序列 ---
        // 这里使用之前封装好的 runSequence，它内部也有消失检测机制
        SequenceClicker.runSequence(listOf(Dungeon.TPL_SELECT_HERO))

        // --- 第四阶段：等待进入角色选择页面 (无限等待) ---
        Log.d(TAG, "等待进入角色选择页面 (TPL_START_GAME)...")
        return SequenceClicker.waitForImage(Dungeon.TPL_START_GAME, 0)
    }
}