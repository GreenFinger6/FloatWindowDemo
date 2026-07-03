package com.example.floatwindowdemo.utils

import android.content.Context
import android.content.Intent

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

