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
     * @param onResult 成功回调
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
     * 停止 OCR（释放资源）
     */
    fun release() {
        recognizer.close()
    }
}
