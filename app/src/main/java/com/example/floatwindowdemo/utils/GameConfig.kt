package com.example.floatwindowdemo.utils

/**
 * 统一管理游戏静态常量( 1237*720 分辨率下)
 */
object GameConfig {

    // 1. 按钮中心坐标 (x, y)
    object Buttons {
        val START = Pair(540f, 1200f)
        val RETRY = Pair(300f, 800f)
        val CLOSE = Pair(1000f, 50f)
        // 拍卖行第一个物品位置
        val PaiMaiHang = Pair(614f, 166f)
        val PaiMaiHang2 = Pair(66f, 415f)
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

    // 3. 文本常量
    object Texts {
        const val VICTORY = "胜利"
        const val DEFEAT = "失败"
        const val LOADING = "加载中"
    }
}

// 定义一个简单的区域数据类
data class RectArea(val x1: Float, val y1: Float, val x2: Float, val y2: Float)