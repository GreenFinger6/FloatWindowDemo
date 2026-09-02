package com.xiaofang.floatwindow.utils

/**
 * 点击任务数据类
 * @property templateName 模板名称
 * @property region 识别区域 (可选)，若为 null 则全屏搜索
 * @property threshold 识别阈值，默认为 0.85
 */
data class ClickTask(
    val templateName: String,
    val region: RectArea? = null,
    val threshold: Double = 0.85
)
