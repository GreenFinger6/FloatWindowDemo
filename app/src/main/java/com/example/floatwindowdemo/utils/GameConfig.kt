package com.example.floatwindowdemo.utils

/**
 * 统一管理游戏静态常量( 1237*720 分辨率下)
 */

// 拍卖行相关常量
object Auction {
    // 1. 按钮中心坐标 (x, y)
    object Buttons {
        val PaiMaiHang = Pair(0.4964f, 0.2306f) // 拍卖行第一个物品位置
        val PaiMaiHang2 = Pair(0.0534f, 0.5764f) // 返回拍卖行
        val PaiMaiHang3 = Pair(66f, 415f) // 购买
        val PaiMaiHang4 = Pair(66f, 415f) // 购买确认
    }

    // 2. 裁剪区域 (x1, y1, x2, y2)
    // 建议使用自定义的数据类或 Rect，这里用自定义简单的封装
    object Regions {
        // 顶部公告区域
        val NOTICE = RectArea(0f, 0f, 1080f, 200f)
        // 底部菜单区域
        val MENU = RectArea(0f, 2000f, 1080f, 2400f)
        // 游戏核心操作区
        val BATTLE_FIELD = RectArea(100f, 400f, 980f, 1800f)
        // 拍卖行最低价区域
        val MIN_PRICE = RectArea(0.1148f, 0.3708f, 0.4770f, 0.4556f)
    }

    // 涉及的相关模版
    val templateList = listOf(
        "state_auction_purchase", //判断购买页面
        "state_auction_detail"  //判断商品详情
    )
}

// 定义一个简单的区域数据类
data class RectArea(val x1: Float, val y1: Float, val x2: Float, val y2: Float)