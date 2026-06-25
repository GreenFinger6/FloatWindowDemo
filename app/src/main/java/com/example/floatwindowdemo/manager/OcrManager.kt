package com.example.floatwindowdemo.manager

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class OcrManager(private val context: Context) {

    // 1. 初始化 ML Kit 识别器
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    /**
     * 执行 OCR 识别
     * @param bitmap 已经裁剪好的区域图片
     * @param onResult 成功回调，返回识别到的文字字符串
     * @param onError 失败回调，返回异常信息
     */
    fun recognizeText(bitmap: Bitmap, onResult: (String) -> Unit, onError: (Exception) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onResult(visionText.text)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * 在图片中查找特定关键字并返回第一个找到的中心坐标
     * @param bitmap 已经裁剪好的区域图片
     * @param keyword 需要识别的关键字
     * @param onFound 成功回调，返回识别到的绝对坐标
     * @param onNotFound 失败回调
     */
    fun findTextLocation(
        bitmap: Bitmap,
        keyword: String,
        onFound: (x: Int, y: Int) -> Unit,
        onNotFound: () -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                var found = false
                // 遍历所有文本块
                for (block in visionText.textBlocks) {
                    // 如果当前块包含关键字（也可以用正则或全匹配）
                    if (block.text.contains(keyword)) {
                        val rect = block.boundingBox
                        if (rect != null) {
                            // 返回中心点坐标
                            onFound(rect.centerX(), rect.centerY())
                            found = true
                            break // 找到第一个就跳出
                        }
                    }
                }
                if (!found) onNotFound()
            }
            .addOnFailureListener {
                onNotFound()
            }
    }

    /**
     * 停止 OCR（释放资源）
     */
    fun stop() {
        recognizer.close()
    }
}
