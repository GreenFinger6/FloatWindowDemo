package com.example.floatwindowdemo.utils

import android.content.res.AssetManager
import android.graphics.Bitmap

object YoloUtil {
    init {
        System.loadLibrary("yolov8n_ncnn")
    }

    // 初始化模型
    external fun initModel(assetManager: AssetManager): Boolean
    // 传入截取的 Bitmap，返回检测到的物体信息
    external fun detect(bitmap: Bitmap,
                        probThreshold: Float = 0.5f,
                        nmsThreshold: Float = 0.5f): Array<DetectionResult>?
    // 释放资源
    external fun release()

    // 这里的顺序必须与你训练模型时的 .yaml 文件中的 names 顺序完全一致
    private val LABELS = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
    )

    fun getLabelName(index: Int): String {
        return if (index in LABELS.indices) LABELS[index] else "Unknown($index)"
    }
}

data class DetectionResult(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val label: Int,
    val prob: Float
){
    // 自动计算中心点 X
    val centerX: Float get() = x + w / 2f
    // 自动计算中心点 Y
    val centerY: Float get() = y + h / 2f
}