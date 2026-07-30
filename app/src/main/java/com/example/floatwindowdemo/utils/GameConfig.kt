package com.example.floatwindowdemo.utils

/**
 * 统一管理游戏静态常量( 1237*720 分辨率下)
 */
// 定义一个简单的区域数据类
data class RectArea(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

// 拍卖行相关常量
object Auction {
    // 1. 按钮中心坐标 (x, y)
    object Buttons {
        val Detail = Pair(0.4964f, 0.2306f) // 拍卖行第一个物品位置
        val Back = Pair(0.0534f, 0.5764f) // 返回
    }

    // 2. 裁剪区域 (x1, y1, x2, y2)
    // 建议使用自定义的数据类或 Rect，这里用自定义简单的封装
    object Regions {
        // 准备购买数量区域
        val PRE_BUY = RectArea(0f, 0f, 1080f, 200f)

        // 成功购买数量区域
        val SUCCESS_BUY = RectArea(0f, 0f, 1080f, 200f)

        // 拍卖行最低价区域
        val MIN_PRICE = RectArea(0.1148f, 0.3708f, 0.4770f, 0.4556f)
    }

    // 涉及的状态检测相关模版
    val stateTemplateList = listOf(
        "state_auction_purchase", //判断购买页面
        "state_auction_detail"  //判断商品详情
    )

    // 购买序列按钮
    val buyList = listOf(
        "button_buy", //购买
        "button_confirm"  //购买确认
    )
}

// 深渊相关
object Dungeon {
    object Buttons {
        val Attack = Pair(0.8618f, 0.8847f) // 攻击
        val Settings = Pair(0.9466f, 0.1819f) // 设置

        // 角色选择界面，每个角色的坐标
        val SelectHeroList = listOf(
            Pair(0.1592f, 0.4778f),
            Pair(0.3333f, 0.4565f),
            Pair(0.5042f, 0.4796f),
            Pair(0.6767f, 0.4602f),
            Pair(0.8529f, 0.4750f),
        )
    }
    object Regions {
        // 城镇体力识别区域
        val TOWN_STAMINA = RectArea(0.1213f, 0.0764f, 0.1770f, 0.125f)
        // 战斗体力识别区域
        val BATTLE_STAMINA = RectArea(0.1277f, 0.0708f,  0.2021f, 0.1292f)
    }


    // 状态检测类模板
    const val TPL_RE_CHALLENGE = "button_re_challenge" // 再次挑战
    const val TPL_BACK_2_TOWN = "button_back2town"     // 返回城镇
    const val TPL_TASK_MENU = "button_task"            // 委托菜单
    const val TPL_DUNGEON_TAB = "button_dungeon"       // 深渊
    const val TPL_CONFIRM = "button_confirm"           // 确认
    const val TPL_DUNGEON_SELECT = "button_dungeon1"   // 具体副本选择
    const val TPL_ENTRY_GATE = "button_entry"          // 入场按钮
    const val TPL_START_GAME = "button_start_game"    // 选择英雄界面开始游戏
    const val TPL_SELECT_HERO = "button_select_hero"    // 设置界面选择角色
    const val TPL_PAST_DUNGEON = "button_past_dungeon"    // 老深渊
    const val TPL_PAST_ENTRUST = "button_past_entrust"    // 过去委托
    const val TPL_PRE_ENTRY = "button_pre_entry"    // 入场

    // 涉及的状态检测相关模版
    val allTemplates = listOf(
        TPL_RE_CHALLENGE,
        TPL_BACK_2_TOWN,
        TPL_TASK_MENU,
        TPL_DUNGEON_TAB,
        TPL_CONFIRM,
        TPL_DUNGEON_SELECT,
        TPL_ENTRY_GATE,
        TPL_START_GAME,
        TPL_SELECT_HERO,
        TPL_PAST_DUNGEON,
        TPL_PAST_ENTRUST,
        TPL_PRE_ENTRY,
    )

    // 进入深渊
    val entrySequence = listOf(
        TPL_TASK_MENU,
        TPL_DUNGEON_TAB,
        TPL_CONFIRM,
        TPL_DUNGEON_SELECT,
        TPL_ENTRY_GATE
    )

    // 进入深渊
    val entryPastDungeon = listOf(
        TPL_TASK_MENU,
        TPL_PAST_ENTRUST,
        TPL_PAST_DUNGEON,
        TPL_PRE_ENTRY,
        TPL_ENTRY_GATE
    )
}