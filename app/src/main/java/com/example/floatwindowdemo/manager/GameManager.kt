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
    BATTLE_FIGHTING,// 战斗中：执行战斗逻辑
    BATTLE_FINISHED,// 战斗结束：下一关or返回城镇
    RECOVERY    // 异常恢复：处理弹窗或卡死
}

class GameManager(private val context: Context) {
    private val TAG = "GameManager"
    private var takeNext = false

    /**
     * 每一帧的入口
     */
    suspend fun onFrame(bitmap: Bitmap): Boolean {

        // 逻辑终点: 所有角色任务完成

        // FSM状态判定
        val priceBitmap = cropBitmap(Dungeon.Regions.BATTLE_STAMINA, bitmap)
        val text = withContext(Dispatchers.Default) {
            OcrManager.recognizeTextAsync(priceBitmap)
        }
        if(text.contains("拾取道具")){
            takeNext = true
        }
        val template1 = OpencvUtil.templateCache[Dungeon.stateTemplateList[0]]
        val template2 = OpencvUtil.templateCache[Dungeon.stateTemplateList[1]]
        if (template1 == null || template2 == null){
            Log.e(TAG,"状态模版加载失败")
            return false
        }
        val again = OpencvUtil.findImage(bitmap, template1)
        val back = OpencvUtil.findImage(bitmap, template2)
        if(takeNext && !text.contains("拾取道具")){
            // 此时刚打完一轮
            if(again!=null) AutomationService.instance?.click(again.x.toFloat(), again.y.toFloat())
            else if(back!=null) {
                // 结束一轮
                AutomationService.instance?.click(back.x.toFloat(), back.y.toFloat())
                return true
            }
            else AutomationService.instance?.click(Dungeon.Buttons.Attack, 500L)
        }else AutomationService.instance?.click(Dungeon.Buttons.Attack,500L)
        return false
    }

    /**
     * 识别当前是处于城镇还是战斗
     * 建议：城镇识别 UI 上的“地图”按钮，战斗识别 UI 上的“技能按键”或“血条”
     */
    private suspend fun detectCurrentState(bitmap: Bitmap): GameState = withContext(Dispatchers.Default) {
        // 使用模板匹配识别状态特征
        val townTemplate = OpencvUtil.templateCache["ui_map_icon"] // 城镇特有图标
        val battleTemplate = OpencvUtil.templateCache["ui_skill_icon"] // 战斗特有图标

        if (townTemplate == null || battleTemplate == null) return@withContext GameState.RECOVERY

        val isTown = async { OpencvUtil.findImage(bitmap, townTemplate) }
        val isBattle = async { OpencvUtil.findImage(bitmap, battleTemplate) }

        when {
            isBattle.await() != null -> GameState.BATTLE_FIGHTING
            isTown.await() != null -> GameState.TOWN
            else -> GameState.RECOVERY
        }
    }

    /**
     * 城镇状态逻辑
     */
    private suspend fun handleTownState(bitmap: Bitmap) {
        // 示例：如果需要进图，这里调用具体的进图方法
        // enterDungeon()
        Log.d(TAG, "当前在城镇...")
    }

    /**
     * 战斗状态逻辑
     */
    private suspend fun handleBattleState(bitmap: Bitmap) {
        // 示例：调用 YOLO 识别怪物并攻击
        // executeCombat(bitmap)
        Log.d(TAG, "正在战斗中...")
    }

    /**
     * 异常恢复
     */
    private suspend fun handleRecovery() {
        Log.w(TAG, "未知状态，尝试按返回键...")
        delay(1000)
    }
}