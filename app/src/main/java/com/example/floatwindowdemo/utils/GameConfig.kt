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
        val InputNum = Pair(1172.5f, 578f) // 数量输入
    }

    // 2. 裁剪区域 (x1, y1, x2, y2)
    // 建议使用自定义的数据类或 Rect，这里用自定义简单的封装
    object Regions {
        // 成功购买数量区域
        val SUCCESS_BUY = RectArea(0.4268f, 0.6f, 0.6621f, 0.6556f)
        // 拍卖行最低价区域
        val MIN_PRICE = RectArea(0.1148f, 0.4194f, 0.4770f, 0.5083f)
        // 拍卖行最低价二次确认区域
        val MIN_PRICE_CONFIRM = RectArea(0.6895f, 0.5944f, 0.8326f, 0.6430f)

        // 按钮快速匹配区域 (ROI)
        val BUY_BTN = RectArea(0.505f, 0.6264f, 0.8833f, 0.9217f)
        val CONFIRM_BTN = RectArea(0.4171f, 0.6652f, 0.6111f, 0.7847f)
        val INPUT_BTN = RectArea(0.6822f, 0.6388f, 0.8439f, 0.7111f)
        val INPUT_MAX_BTN = RectArea(0.8270f, 0.7222f, 0.8973f, 0.8541f)
    }

    // 状态检测类模板
    const val TPL_PURCHASE = "state_auction_purchase" // 判断购买页面
    const val TPL_DETAIL = "state_auction_detail"     // 判断商品详情
    const val TPL_BUY = "button_buy"                  //购买
    const val TPL_CONFIRM = "button_confirm"          //购买确认
    const val TPL_INPUT_NUM = "button_inputNum"        //点击输入购买数量
    const val TPL_INPUT_MAX = "button_inputMax"        //最大数量输入
    const val TPL_INPUT_CONFIRM = "button_inputConfirm"//数量输入
    const val TPL_INPUT_NUM_0 = "button_inputNum_0"    //数字0
    const val TPL_INPUT_NUM_1 = "button_inputNum_1"    //数字1
    const val TPL_INPUT_NUM_2 = "button_inputNum_2"    //数字2
    const val TPL_INPUT_NUM_3 = "button_inputNum_3"    //数字3
    const val TPL_INPUT_NUM_4 = "button_inputNum_4"    //数字4
    const val TPL_INPUT_NUM_5 = "button_inputNum_5"    //数字5
    const val TPL_INPUT_NUM_6 = "button_inputNum_6"    //数字6
    const val TPL_INPUT_NUM_7 = "button_inputNum_7"    //数字7
    const val TPL_INPUT_NUM_8 = "button_inputNum_8"    //数字8
    const val TPL_INPUT_NUM_9 = "button_inputNum_9"    //数字9


    // 涉及的状态检测相关模版
    val allTemplates = listOf(
        TPL_PURCHASE,
        TPL_DETAIL,
        TPL_BUY,
        TPL_CONFIRM,
        TPL_INPUT_NUM,
        TPL_INPUT_MAX,
        TPL_INPUT_CONFIRM,
        TPL_INPUT_NUM_0,
        TPL_INPUT_NUM_1,
        TPL_INPUT_NUM_2,
        TPL_INPUT_NUM_3,
        TPL_INPUT_NUM_4,
        TPL_INPUT_NUM_5,
        TPL_INPUT_NUM_6,
        TPL_INPUT_NUM_7,
        TPL_INPUT_NUM_8,
        TPL_INPUT_NUM_9
    )

    // 购买序列按钮
    val buyList = listOf(
        TPL_BUY,     //购买
        TPL_CONFIRM  //购买确认
    )

    /**
     * 根据数字生成对应模版点击的list
     * @param number 要输入的数字
     * @return 模版名称列表
     */
    fun getNumberTemplates(number: Long): List<String> {
        val digitMap = mapOf(
            '0' to TPL_INPUT_NUM_0, '1' to TPL_INPUT_NUM_1, '2' to TPL_INPUT_NUM_2,
            '3' to TPL_INPUT_NUM_3, '4' to TPL_INPUT_NUM_4, '5' to TPL_INPUT_NUM_5,
            '6' to TPL_INPUT_NUM_6, '7' to TPL_INPUT_NUM_7, '8' to TPL_INPUT_NUM_8,
            '9' to TPL_INPUT_NUM_9
        )
        return listOf(TPL_INPUT_NUM) + number.toString().mapNotNull { digitMap[it] }
    }
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
        val BATTLE_STAMINA = RectArea(0.1083f, 0.7778f,  0.2263f, 0.1231f)
    }


    // 状态检测类模板
    const val TPL_PICK_UP = "state_game_pickup" // 拾取物品
    const val TPL_RE_CHALLENGE = "button_re_challenge" // 再次挑战
    const val TPL_BACK_2_TOWN = "button_back2town"     // 返回城镇
    const val TPL_REPAIR_EQUIP = "button_repairEquip"     // 修理装备
    const val TPL_REPAIR_EQUIP1 = "button_repairEquip1"     // 修理装备1
    const val TPL_CLOSE = "button_close"     // 关闭
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
    const val TPL_SECRET_SHOP = "button_secret_shop"    // 神秘商店
    const val TPL_SECRET_SHOP_REFRESH = "button_secret_shop_refresh"    // 神秘商店免费刷新
    const val TPL_SECRET_SHOP_BUY = "button_secret_shop_buy"    // 神秘商店购买
    const val TPL_SECRET_SHOP_BUY2 = "button_secret_shop_buy2"    // 神秘商店购买2
    const val TPL_BAG = "button_bag"                // 背包
    const val TPL_DECOMPOSE = "button_decompose"    // 分解
    const val TPL_DECOMPOSE2 = "button_decompose2"    // 分解2
    const val TPL_DECOMPOSE_CONFIRM = "button_decompose_confirm"    // 分解确认
    const val TPL_EMAIL = "button_email"    // 邮件
    const val TPL_CLAIM_MAIL = "button_claimMail"    // 领取邮件

    // 涉及的所有模版列表统一导入
    val allTemplates = listOf(
        TPL_PICK_UP,
        TPL_RE_CHALLENGE,
        TPL_BACK_2_TOWN,
        TPL_REPAIR_EQUIP,
        TPL_REPAIR_EQUIP1,
        TPL_CLOSE,
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
        TPL_SECRET_SHOP,
        TPL_SECRET_SHOP_REFRESH,
        TPL_SECRET_SHOP_BUY,
        TPL_SECRET_SHOP_BUY2,
        TPL_BAG,
        TPL_DECOMPOSE,
        TPL_DECOMPOSE2,
        TPL_DECOMPOSE_CONFIRM,
        TPL_EMAIL,
        TPL_CLAIM_MAIL
    )

    // 进入多维秘境
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

    // key:副本名称，value:步骤List
    val dungeonStepMap: Map<String, List<String>> = mapOf(
        "深渊秘境" to entrySequence,
        "老深渊" to entryPastDungeon
    )

    // 背包分解
    val decomposeBag = listOf(
        TPL_BAG,
        TPL_DECOMPOSE,
        TPL_DECOMPOSE2,
        TPL_DECOMPOSE_CONFIRM,
        TPL_CONFIRM
    )

    // 修理装备
    val repairEquipment = listOf(
        TPL_REPAIR_EQUIP,
        TPL_REPAIR_EQUIP1
    )
}