package com.example.floatwindowdemo.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

object OpencvUtil {
    private var isInitialized = false
    private const val TAG = "OpencvUtil"
    val templateCache = mutableMapOf<String, Bitmap>() //缓存 Map，存放预加载的模板

    private fun checkInit(): Boolean {
        if (!isInitialized) {
            isInitialized = OpenCVLoader.initDebug()
        }
        return isInitialized
    }

    /**
     * 在源图(source)中查找模板图(template)
     * @param source 大图（通常是屏幕截图）
     * @param template 小图（你要找的图标或按钮）
     * @param threshold 相似度阈值 (0.0 ~ 1.0)，建议 0.8
     * @return 匹配目标的中心点坐标，如果未找到则返回 null
     */
    fun findImage(source: Bitmap, template: Bitmap, threshold: Double = 0.9): Point? {

        // 每次调用前先检查
        if (!checkInit()) {
            Log.e(TAG, "OpenCV 未初始化")
            return null
        }

        val srcMat = Mat()
        val tempMat = Mat()

        try {
            // 1. 将 Bitmap 转为 Mat
            Utils.bitmapToMat(source, srcMat)
            Utils.bitmapToMat(template, tempMat)

            // 2. 转为灰度图（模板匹配在灰度下速度更快，且更鲁棒）
            Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(tempMat, tempMat, Imgproc.COLOR_RGBA2GRAY)

            // 3. 创建结果矩阵
            val result = Mat()
            val resultCols = srcMat.cols() - tempMat.cols() + 1
            val resultRows = srcMat.rows() - tempMat.rows() + 1
            result.create(resultRows, resultCols, CvType.CV_32FC1)

            // 4. 执行模板匹配 (使用归一化相关系数匹配法，效果最稳定)
            Imgproc.matchTemplate(srcMat, tempMat, result, Imgproc.TM_CCOEFF_NORMED)

            // 5. 寻找最大匹配值及其位置
            val mmr = Core.minMaxLoc(result)
            val maxVal = mmr.maxVal // 相似度分数
            val maxLoc = mmr.maxLoc // 匹配到的左上角坐标

//            Log.d(TAG, "匹配得分: $maxVal")

            // 6. 释放 Mat 内存（非常重要，防止内存泄漏）
            result.release()

            if (maxVal >= threshold) {
                // 返回匹配区域的中心点
                return Point(
                    maxLoc.x + template.width / 2.0,
                    maxLoc.y + template.height / 2.0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "匹配出错: ${e.message}")
        } finally {
            srcMat.release()
            tempMat.release()
        }
        return null
    }

    /**
     * 从 Assets 文件夹读取图片并缓存
     * @param taskList 图片路径list
     */
     fun preloadTemplates(context: Context, taskList: List<String>) {
        taskList.forEach { taskName ->
            // 假设图片名和任务名一致
            val fileName = "templates/$taskName.png"
            val bitmap = loadBitmapFromAssets(context, fileName)
            if (bitmap != null) {
                templateCache[taskName] = bitmap
            }
        }
    }

    /**
     * 从 Assets 文件夹读取图片并转换为 Bitmap
     * @param fileName 相对于 assets目录的路径，例如 "templates/button_start.png"
     */
    fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap? {
        return try {
            context.assets.open(fileName).use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "无法读取 Assets 图片: $fileName, 错误: ${e.message}")
            null
        }
    }

    /**
     * 保存bitmap图片到应用缓存路径
     * @param bitmap 图片
     */
    suspend fun saveDebugBitmap(context: Context, bitmap: Bitmap) {
        // 切换到 IO 线程并“挂起”等待
        withContext(Dispatchers.IO) {
            try {
                // data -> data -> com.example.floatwindowdemo -> cache -> debug_frame.png。
                val file = java.io.File(context.cacheDir, "debug_frame.png")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.d(TAG, "调试图片已保存至: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "保存图片失败: ${e.message}")
            }
        }
    }

    fun releaseTemplates() {
        // 1. 遍历 Map 中所有的 Bitmap 值，逐个调用 recycle() 释放 Native 内存
        templateCache.values.forEach { it.recycle() }

        // 2. 清空 Map，断开 Java 对象引用，让 Java GC 可以回收包装对象
        templateCache.clear()
    }
}