package com.example.floatwindowdemo.utils

import android.content.Context

object DensityUtil {
    // dp转px（安卓所有布局推荐用dp单位，代码中需要转换成px）
    fun dp2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    // px转dp
    fun px2dp(context: Context, pxValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }
}