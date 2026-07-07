package com.example.floatwindowdemo.utils

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {
    private const val PREF_NAME = "float_script_settings"

    // Key 常量定义
    private const val KEY_MAX_PRICE = "max_price"
    private const val KEY_MAX_QUANTITY = "max_quantity"
    private const val KEY_MIAO_CODE = "miao_code"


    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // --- 拍卖行设置 ---
    fun saveAuctionConfig(context: Context, config: AuctionConfig) {
        getPrefs(context).edit().apply {
            putLong(KEY_MAX_PRICE, config.maxPrice)
            putLong(KEY_MAX_QUANTITY, config.maxQuantity)
            apply()
        }
    }
    fun getAuctionConfig(context: Context) : AuctionConfig{
        val maxPrice = getPrefs(context).getLong(KEY_MAX_PRICE, 0L)
        val maxQuantity = getPrefs(context).getLong(KEY_MAX_QUANTITY, 0L)
        return AuctionConfig(maxPrice, maxQuantity)
    }

    // --- 喵提醒 ---
    fun saveMiaoCode(context: Context, id: String) {
        getPrefs(context).edit().apply {
            putString(KEY_MIAO_CODE, id)
            apply()
        }
    }

    fun getMiaoCode(context: Context) : String?{
        val id = getPrefs(context).getString(KEY_MIAO_CODE, "")
        return id
    }
}

// 拍卖行抢拍设置
data class AuctionConfig(
    val maxPrice: Long = 0,
    val maxQuantity: Long = 0
)