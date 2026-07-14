package com.example.floatwindowdemo.manager

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.createBitmap

class ScreenCaptureManager(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isStreaming = false // 是否处于持续识别状态

    fun init(resultCode: Int, data: Intent) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
    }

    /**
     * 开启流式捕捉
     * @param onFrameCaptured 提供 Bitmap
     */
    fun startStreaming(onFrameCaptured: (bitmap: Bitmap, onTaskComplete: () -> Unit) -> Unit) {
        if (isStreaming || mediaProjection == null) return
        isStreaming = true

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // 1. 保持 ImageReader 存活，缓冲区设为 2 帧，确保总能拿到最新的
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        // 2. 保持 VirtualDisplay 存活
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "StreamingCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        // 3. 定义截取函数
        fun captureNext() {
            if (!isStreaming) return

            // acquireLatestImage 保证拿到的是屏幕当前的最新画面，而不是之前排队的旧图
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val bitmap = processImage(image, width, height)
                image.close()

                // 将 bitmap 发送给 OCR，并传入一个“完成证明”
                onFrameCaptured(bitmap) {
                    // 当 OCR 识别结束（无论成功失败）时，外部调用这个 lambda
                    // 从而触发下一轮截图，形成“链式反应”
                    if (isStreaming) {
                        handler.post { captureNext() }
                    }
                }
            } else{
                // 如果当前由于某些原因没拿到图，延迟一个极短的时间重试
                handler.postDelayed({ captureNext() }, 10)
            }
        }
        // 4. 开启第一轮
        handler.postDelayed({ captureNext() }, 100) // 初始延迟给 VirtualDisplay 一点准备时间
    }

    // 将 ImageReader 的原始数据转为 Bitmap 的专业处理
    private fun processImage(image: android.media.Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        // 考虑到填充（Row Padding），我们需要重新计算宽度
        val bitmap = createBitmap(width + rowPadding / pixelStride, height)
        bitmap.copyPixelsFromBuffer(buffer)

        // 如果有填充，裁剪掉多余边缘
        return if (rowPadding > 0) {
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()
            croppedBitmap
        } else {
            bitmap
        }
    }

    fun stopStreaming() {
        isStreaming = false
        // 传入 null 代表移除该 Handler 关联的所有回调
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    // 3. 停止，释放资源
    fun stop() {
        stopStreaming()
        mediaProjection?.stop()
        mediaProjection = null
    }
}
