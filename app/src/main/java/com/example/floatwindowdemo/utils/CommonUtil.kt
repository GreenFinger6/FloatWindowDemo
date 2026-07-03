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

