package com.example.floatwindowdemo.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object ConfigManager {
    private const val PREF_NAME = "float_script_settings"

    // Key 常量定义
    private const val KEY_MAX_PRICE = "max_price" // 拍卖行最高价格
    private const val KEY_MAX_QUANTITY = "max_quantity" // 拍卖行最多数量
    private const val KEY_MIAO_CODE = "miao_code" //喵提醒码
    private const val KEY_MAIN_TASK = "main_task" // 当前任务
    private const val KEY_ROLE_DATA = "role_data" // 存放 JSON 字符串

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 保存主任务选择
    fun saveMainTask(context: Context, index: Int) {
        getPrefs(context).edit { putInt(KEY_MAIN_TASK, index) }
    }
    fun getMainTask(context: Context): Int = getPrefs(context).getInt(KEY_MAIN_TASK, 0)

    // --- 角色详情数据 (JSON字符串) ---
    fun saveRoleDataJson(context: Context, json: String) {
        getPrefs(context).edit { putString(KEY_ROLE_DATA, json) }
    }
    fun getRoleDataJson(context: Context): String = getPrefs(context).getString(KEY_ROLE_DATA, "") ?: ""

    // --- 拍卖行设置 ---
    fun saveAuctionConfig(context: Context, config: AuctionConfig) {
        getPrefs(context).edit {
            putLong(KEY_MAX_PRICE, config.maxPrice)
            putLong(KEY_MAX_QUANTITY, config.maxQuantity)
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


// 角色配置设置
data class RoleData(
    val isEnabled: Boolean, // 是否启用
    val daily: Boolean,
    val boss: Boolean,
    val decompose: Boolean,
    val mail: Boolean
)