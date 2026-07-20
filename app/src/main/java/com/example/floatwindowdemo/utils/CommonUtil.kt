package com.example.floatwindowdemo.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject

fun installApk(context: Context, apkFile: java.io.File) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    // 通过 FileProvider 获取安全 URI
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )

    intent.setDataAndType(uri, "application/vnd.android.package-archive")
    context.startActivity(intent)
}


/**
 * 提取价格/金额逻辑：
 * 1. 匹配所有数字和逗号的组合
 * 2. 去掉逗号转换成 Long (考虑到游戏金额可能超过 Int 的 21亿上限)
 */
fun extractPrice(text: String): Long {
    // Regex: 匹配连续的数字或带逗号的数字
    val regex = Regex("""(\d[\d,]*)""")
    val match = regex.find(text)?.value ?: return 0L
    // 去掉逗号并转为 Long
    return match.replace(",", "").toLongOrNull() ?: 0L
}


/**
 * 提取数量逻辑：
 * 1. 匹配“数量”关键字及其后面跟随的数字和逗号组合
 * 2. 支持格式如：“数量 2”、“数量: 4,878,016”、“数量1,000”
 */
fun extractQuantity(text: String): Long {
    // Regex 解释：
    // 数量\D* : 匹配“数量”二字以及后面任意个非数字字符（如冒号、空格）
    // ([\d,]+) : 捕获组，匹配连续的数字或逗号
    val regex = Regex("""数量\D*([\d,]+)""")
    val matchResult = regex.find(text)

    // 获取第一个捕获组的值 (即括号里的部分)
    val match = matchResult?.groups?.get(1)?.value ?: return 0L

    // 同样去掉逗号并转为 Long，因为数量也可能很大
    return match.replace(",", "").toLongOrNull() ?: 0L
}

/**
 * 提取当前体力值逻辑：
 * @return 成功返回当前体力(Int)，识别失败或格式错误返回 -1
 */
/**
 * 提取当前体力值：处理 O/0 混淆，并提取当前值
 */
fun extractStamina(text: String): Int {
    if (text.isBlank()) return -1

    // 1. 字符纠错：将字母 O 替换为数字 0 (解决 O100 问题)
    val correctedText = text.replace("O", "0")
        .replace("o", "0")
        .replace("I", "1")
        .replace("l", "1")

    // 2. 清洗：去掉除了数字和斜杠以外的一切字符
    val cleanText = correctedText.replace(Regex("[^0-9/]"), "")

    // 3. 情况 A：有斜杠 (例如 "0/100" 或 "120/100")
    if (cleanText.contains("/")) {
        val parts = cleanText.split("/")
        if (parts.isNotEmpty()) {
            return parts[0].takeLast(3).toIntOrNull() ?: -1
        }
    }

    // 4. 情况 B：没斜杠粘连了 (例如 "0100" 或 "120100")
    // 正则：匹配 1-3 位数字 + 后缀固定的 100
    val stickyRegex = Regex("""(\d{1,3})(100)$""")
    val matchResult = stickyRegex.find(cleanText)
    if (matchResult != null) {
        return matchResult.groupValues[1].toIntOrNull() ?: -1
    }

    // 5. 情况 C：极其简略的单数值
    if (cleanText.length in 1..3) {
        return cleanText.toIntOrNull() ?: -1
    }

    return -1
}

// 裁剪 Bitmap 的专业处理
fun cropBitmap(rectArea: RectArea, bitmap: Bitmap): Bitmap {
    try {
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        // 1. 将比例换算为真实像素
        val left = (rectArea.x1 * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
        val top = (rectArea.y1 * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
        val right = (rectArea.x2 * bitmapWidth).toInt().coerceIn(left + 1, bitmapWidth)
        val bottom = (rectArea.y2 * bitmapHeight).toInt().coerceIn(top + 1, bitmapHeight)

        // 2. 计算宽度和高度
        val width = right - left
        val height = bottom - top

        // 3. 执行裁剪
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    } catch (e: Exception) {
        Log.e("CommonUtil", "裁剪失败，返回原图: ${e.message}")
        return bitmap
    }
}

/**
 * 检测 Bitmap 是否为真实画面（非全黑/全透明）
 * 采样检测中心点及四个象限点
 */
fun isRealFrame(bitmap: Bitmap): Boolean {
    val w = bitmap.width
    val h = bitmap.height

    // 采样 5 个点：中心、左上、右上、左下、右下
    val points = arrayOf(
        Pair(w / 2, h / 2),
        Pair(w / 4, h / 4),
        Pair(3 * w / 4, h / 4),
        Pair(w / 4, 3 * h / 4),
        Pair(3 * w / 4, 3 * h / 4)
    )

    for (point in points) {
        val pixel = bitmap.getPixel(point.first, point.second)
        // 在 RGBA_8888 中，如果 pixel 不为 0，说明该点有颜色或透明度
        if (pixel != 0) return true
    }

    return false
}

/***
 * 发送喵提醒
 * id: 用户id
 * text：提醒消息
 */
suspend fun postMiao(id: String, text: String): Boolean{
    // 直接构建 JSON 对象
    val params = JSONObject().apply {
        put("id", id)
        put("text", text)
    }
    // 异步发送
    return NetworkUtil.sendPost("https://miaotixing.com/trigger", params)
}
