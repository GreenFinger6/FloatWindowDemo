package com.example.floatwindowdemo.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object ConfigManager {
    private const val PREF_NAME = "float_script_settings"

    // Key 常量定义
    private const val KEY_MAX_PRICE = "max_price" // 拍卖行最高价格
    private const val KEY_MAX_QUANTITY = "max_quantity" // 拍卖行最多数量
    private const val KEY_IS_GREEDY = "is_greedy" // 是否贪心模式
    private const val KEY_MIAO_CODE = "miao_code" //喵提醒码
    private const val KEY_MAIN_TASK = "main_task" // 当前任务
    private const val KEY_ROLE_DATA = "role_data" // 存放 JSON 字符串
    
    // --- 定时启动配置 ---
    private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    private const val KEY_SCHEDULE_HOUR = "schedule_hour"
    private const val KEY_SCHEDULE_MINUTE = "schedule_minute"
    private const val KEY_SCHEDULE_REPEAT = "schedule_repeat"

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
            putBoolean(KEY_IS_GREEDY, config.isGreedy)
        }
    }
    fun getAuctionConfig(context: Context) : AuctionConfig{
        val maxPrice = getPrefs(context).getLong(KEY_MAX_PRICE, 0L)
        val maxQuantity = getPrefs(context).getLong(KEY_MAX_QUANTITY, 0L)
        val isGreedy = getPrefs(context).getBoolean(KEY_IS_GREEDY, false)
        return AuctionConfig(maxPrice, maxQuantity, isGreedy)
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

    fun saveScheduleConfig(context: Context, config: ScheduleConfig) {
        getPrefs(context).edit {
            putBoolean(KEY_SCHEDULE_ENABLED, config.isEnabled)
            putInt(KEY_SCHEDULE_HOUR, config.hour)
            putInt(KEY_SCHEDULE_MINUTE, config.minute)
            putBoolean(KEY_SCHEDULE_REPEAT, config.isRepeatDaily)
        }
    }

    fun getScheduleConfig(context: Context): ScheduleConfig {
        val prefs = getPrefs(context)
        return ScheduleConfig(
            isEnabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false),
            hour = prefs.getInt(KEY_SCHEDULE_HOUR, 8),
            minute = prefs.getInt(KEY_SCHEDULE_MINUTE, 0),
            isRepeatDaily = prefs.getBoolean(KEY_SCHEDULE_REPEAT, true)
        )
    }
}

// 拍卖行抢拍设置
data class AuctionConfig(
    val maxPrice: Long = 0,
    val maxQuantity: Long = 0,
    val isGreedy: Boolean = false
)


// 角色配置设置
data class RoleData(
    val isEnabled: Boolean, // 是否启用
    val dailyTask: String,   // 日常任务类型：深渊秘境、老深渊
    val boss: Boolean,
    val decompose: Boolean,
    val mail: Boolean
)

// 定时启动配置
data class ScheduleConfig(
    val isEnabled: Boolean,
    val hour: Int,
    val minute: Int,
    val isRepeatDaily: Boolean
)
