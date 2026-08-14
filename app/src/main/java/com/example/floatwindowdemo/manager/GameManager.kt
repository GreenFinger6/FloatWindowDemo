package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.utils.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.opencv.core.Point

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
    private val miaoCode = ConfigManager.getMiaoCode(context) // 喵提醒
    private val roleList: List<RoleData> = Gson().fromJson(ConfigManager.getRoleDataJson(context), object : TypeToken<List<RoleData>>() {}.type)
    /**
     * 每一帧的入口
     */
    suspend fun onFrame(bitmap: Bitmap): Boolean {
        // 逻辑出口：所有角色任务执行完毕
        if(countHero > roleList.size) return true

        // 全局拦截器：优先处理确认弹窗（防止其阻塞所有逻辑）
        val confirmLoc = withContext(Dispatchers.Default) {
            OpencvUtil.findInFrame(bitmap, Dungeon.TPL_CONFIRM)
        }
        if (confirmLoc != null) {
            Log.d(TAG, "检测到确认弹窗，优先点击处理")
            AutomationService.instance?.click(confirmLoc)
            // 处理了弹窗后，本帧直接返回，不执行后续状态逻辑，等下一帧环境“干净”后再检测
            return false
        }

        // 3. 状态分发逻辑
        val state = detectCurrentState()
        when (state) {
            GameState.BATTLE -> {
                if(autoBattle(bitmap)){
                    // 当前角色战斗结束
                    Log.i(TAG,"角色${countHero+1}战斗结束")

                    // 神秘商店
                    secretShop()

                    // 分解装备
                    SequenceClicker.runSequence(Dungeon.decomposeBag)

                    // 领取邮件
                    claimMail()

                    // 返回角色选择界面
                    backSelectHero()

                    // 喵提醒
                    if (miaoCode != null) postMiao(miaoCode, "角色${countHero+1}任务完成")

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

                        // 处理可能出现的公告
                        closeAd()

                        // 进入深渊
                        SequenceClicker.runSequence(Dungeon.entryPastDungeon)

                        // 更新是否在城镇
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
     * 自动战斗，直到疲劳或门票耗尽返回城镇
     */
    suspend fun autoBattle (bitmap: Bitmap) : Boolean{
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
            // 业务决策逻辑 (分支流转)
            when {
                // 优先级 : 结算阶段处理 (曾经检测到拾取，且当前拾取框已消失)
                !currentHasPickUp -> {

                    // 1. 并行启动三个识别任务
                    val againMatch = async { OpencvUtil.findInFrame(bitmap, Dungeon.TPL_RE_CHALLENGE) }
                    val backMatch = async { OpencvUtil.findInFrame(bitmap, Dungeon.TPL_BACK_2_TOWN) }
                    val repairMatch = async { OpencvUtil.findInFrame(bitmap, Dungeon.TPL_REPAIR_EQUIP) }

                    // 2. 等待识别结果
                    val againLoc = againMatch.await()
                    val backLoc = backMatch.await()
                    val repairLoc = repairMatch.await()

                    when {
                        againLoc != null -> {
                            Log.i(TAG, "点击：再次挑战")
                            AutomationService.instance?.click(againLoc)
                            delay(UI_CD*4)      // 点击后稍微缓冲
                            false
                        }
                        repairLoc != null ->{
                            Log.i(TAG, "点击：修理装备")
                            if(SequenceClicker.runSequence(Dungeon.repairEquipment)){
                                // 关闭维修装备窗口
                                AutomationService.instance?.performBack()
                            }
                            false
                        }
                        backLoc != null -> {
                            Log.i(TAG, "点击：返回城镇")
                            AutomationService.instance?.click(backLoc)
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
     * 关闭可能的公告
     */
    suspend fun  closeAd(): Boolean{
        var count = 0 // 连续帧符合条件才成功进入
        while (count < 15) {
            val frame = ScreenCaptureManager.frameFlow.first()
            try {
                val emailLoc = OpencvUtil.findInFrame(frame,Dungeon.TPL_SECRET_SHOP)
                val confirmLoc = OpencvUtil.findInFrame(frame,Dungeon.TPL_CONFIRM)
                if (emailLoc != null && confirmLoc == null) {
                    // 以神秘商店与确认按钮来判断是否成功进入游戏
                    count++
                }else{
                    // 尝试返回
                    AutomationService.instance?.performBack()
                    // 给 UI 反应时间
                    delay(1200L)
                }
            } finally {
                frame.recycle()
            }
        }
        Log.i(TAG, "成功进入游戏")
        return true
    }

    /**
     * 在角色选择界面选择指定角色进入游戏
     */
    suspend fun switchHero(targetHero: Int): Boolean{
        var tmp = targetHero
        // 切换目标角色
        Log.i(TAG,"选择角色${tmp+1}")

        // 检测是否在角色选择界面
        if(SequenceClicker.waitForImage(Dungeon.TPL_START_GAME)){
            delay(UI_CD*10) // 等待可能的卡顿
        }

        // 开始向下滑动
        while (tmp >= 5){
            // 下移到下一栏角色
            AutomationService.instance?.swipe(Pair(623f, 508f), Pair(623f, 100f), 1000)
            tmp -= 5
        }
        delay(UI_CD*4)

        // 选择对应角色
        val heroButton = Dungeon.Buttons.SelectHeroList[tmp]
        AutomationService.instance?.click(heroButton)

        // 开始游戏
        SequenceClicker.runSequence(listOf(Dungeon.TPL_START_GAME))

        return true
    }

    /**
     * 返回角色选择
     */
    suspend fun backSelectHero(): Boolean {
        Log.d(TAG, "返回角色选择")

        // 循环打开设置菜单 ---
        while (true) {
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

        // 选择角色
        SequenceClicker.runSequence(listOf(Dungeon.TPL_SELECT_HERO))

        // 等待进入角色选择页面
        Log.i(TAG, "等待进入角色选择页面...")
        return SequenceClicker.waitForImage(Dungeon.TPL_START_GAME)
    }

    /**
     * 神秘商店自动化逻辑：进入商店 -> 自动购买 -> 免费刷新 -> 退出
     */
    suspend fun secretShop(): Boolean {
        Log.i(TAG, "开始执行神秘商店任务")

        // 循环寻找神秘商店图标
        while (true) {
            // 尝试返回
            AutomationService.instance?.performBack()
            // 给 UI 反应时间
            delay(1200L)
            val frame = ScreenCaptureManager.frameFlow.first()
            try {
                val foundMenu = OpencvUtil.findInFrame(frame,Dungeon.TPL_SECRET_SHOP)
                if (foundMenu != null) {
                    Log.d(TAG, "检测到神秘商店图标")
                    break // 菜单已开，跳出循环
                }
            } finally {
                frame.recycle()
            }
        }

        // 1. 尝试进入商店
        val entryLoc = retryFind(Dungeon.TPL_SECRET_SHOP, 10)
        if (entryLoc == null) {
            Log.w(TAG, "未检测到神秘商店图标，跳过该任务")
            return false
        }

        Log.d(TAG, "点击神秘商店")
        AutomationService.instance?.click(entryLoc)
        delay(UI_CD * 4) // 等待商店界面打开

        // 2. 商店内部逻辑循环 (优先级：购买 > 刷新 > 确认)
        val targets = listOf(
            Dungeon.TPL_SECRET_SHOP_BUY to "发起购买",
            Dungeon.TPL_SECRET_SHOP_BUY2 to "清单购买",
            Dungeon.TPL_SECRET_SHOP_REFRESH to "免费刷新",
            Dungeon.TPL_CONFIRM to "购买确认"
        )

        var continuousMissCount = 0
        val maxMissCount = 3

        while (continuousMissCount < maxMissCount) {
            val frame = ScreenCaptureManager.frameFlow.first()
            var actionTaken = false

            try {
                // 优雅地寻找第一个匹配的目标
                targets.firstNotNullOfOrNull { (tpl, msg) ->
                    OpencvUtil.findInFrame(frame, tpl)?.let { it to msg }
                }?.let { (loc, msg) ->
                    Log.i(TAG, msg)
                    AutomationService.instance?.click(loc)
                    actionTaken = true
                }
            } finally {
                frame.recycle()
            }

            if (actionTaken) {
                continuousMissCount = 0
                delay(UI_CD * 3) // 执行动作后等待UI反馈
            } else {
                continuousMissCount++
                delay(UI_CD * 2)
            }
        }

        Log.i(TAG, "神秘商店任务执行完毕")
        AutomationService.instance?.performBack()
        return true
    }

    /**
     * 领取角色邮件：点击邮件 -> 寻找可能领取的物品
     */
    suspend fun claimMail(): Boolean{
        Log.i(TAG, "开始领取角色邮件")

        // 循环寻找邮件图标
        while (true) {
            // 尝试返回
            AutomationService.instance?.performBack()
            // 给 UI 反应时间
            delay(1200L)
            val frame = ScreenCaptureManager.frameFlow.first()
            try {
                val emailLoc = OpencvUtil.findInFrame(frame,Dungeon.TPL_EMAIL)
                if (emailLoc != null) {
                    Log.d(TAG, "检测到邮件图标")
                    AutomationService.instance?.click(emailLoc)
                    break
                }
            } finally {
                frame.recycle()
            }
        }

        // 尝试领取邮件
        val loc = retryFind(Dungeon.TPL_CLAIM_MAIL, 10)
        if (loc == null) {
            Log.w(TAG, "未检测到可领取的邮件，跳过该任务")
            return false
        }

        Log.i(TAG, "点击领取邮件")
        AutomationService.instance?.click(loc)
        return true
    }

    /**
     * 通用的重试查找模板辅助方法
     */
    private suspend fun retryFind(target: String, retries: Int): Point? {
        repeat(retries) {
            val frame = ScreenCaptureManager.frameFlow.first()
            try {
                val loc = OpencvUtil.findInFrame(frame, target)
                if (loc != null) return loc
            } finally {
                frame.recycle()
            }
            delay(UI_CD * 2)
        }
        return null
    }
}