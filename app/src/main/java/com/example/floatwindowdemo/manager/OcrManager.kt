package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
data class OcrLocation(val x: Int, val y: Int)
class OcrManager(private val context: Context) {

    // 1. 初始化 ML Kit 识别器
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 执行 OCR 识别
     * @param bitmap 已经裁剪好的区域图片
     */
    suspend fun recognizeTextAsync(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text) // 恢复协程并返回结果
            }
            .addOnFailureListener { e ->
                Log.e("OcrManager", "OCR 识别失败: ${e.message}")
                continuation.resume("")
            }
    }

    /**
     * 在图片中查找特定关键字并返回第一个找到的中心坐标（挂起函数版）
     * @param bitmap 图片
     * @param keyword 需要识别的关键字
     * @return 找到的坐标对象，若未找到或识别失败则返回 null
     */
    suspend fun findTextLocationAsync(
        bitmap: Bitmap,
        keyword: String
    ): OcrLocation? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                var foundLocation: OcrLocation? = null

                // 遍历所有文本块
                for (block in visionText.textBlocks) {
                    if (block.text.contains(keyword)) {
                        val rect = block.boundingBox
                        if (rect != null) {
                            foundLocation = OcrLocation(rect.centerX(), rect.centerY())
                            break // 找到第一个就跳出循环
                        }
                    }
                }

                // 恢复协程并返回结果（可能是坐标，也可能是 null）
                continuation.resume(foundLocation)
            }
            .addOnFailureListener { e ->
                Log.e("OcrManager", "OCR 识别出错: ${e.message}")
                // 失败时也恢复协程，返回 null
                continuation.resume(null)
            }
    }

    /**
     * 停止 OCR（释放资源）
     */
    fun stop() {
        recognizer.close()
    }
}
