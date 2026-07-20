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
    }
    object Regions {
        // 城镇体力识别区域
        val TOWN_STAMINA = RectArea(0.1221f, 0.0861f, 0.1762f, 0.1111f)
        // 战斗体力识别区域
        val BATTLE_STAMINA = RectArea(0.1067f, 0.0806f, 0.1989f, 0.1208f)
    }
    // 涉及的状态检测相关模版
    val stateTemplateList = listOf(
        "state_auction_purchase", //判断购买页面
        "state_auction_detail"  //判断商品详情
    )
}