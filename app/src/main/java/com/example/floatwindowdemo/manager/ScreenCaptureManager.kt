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
import android.os.HandlerThread
import android.util.Log
import androidx.core.graphics.createBitmap
import com.example.floatwindowdemo.utils.isRealFrame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object ScreenCaptureManager {
    private val TAG = "ScreenCaptureManager"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // 使用专用的后台线程处理图像，防止主线程（UI）卡顿
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isStreaming = false

    // 使用 Channel 处理内存回收
    private val _frameChannel = Channel<Bitmap>(Channel.CONFLATED) { undelivered ->
        if (!undelivered.isRecycled) undelivered.recycle()
    }

    // 暴露为 Flow 供外部 collect
    val frameFlow = _frameChannel.receiveAsFlow()

    /**
     * 初始化 MediaProjection
     * 在 Activity 的授权结果回调后调用
     */
    fun init(context: Context, resultCode: Int, data: Intent) {
        val projectionManager = context.applicationContext // 必须用 applicationContext
            .getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
    }

    fun startStreamingFlow(context: Context) {
        if (isStreaming || mediaProjection == null) return
        isStreaming = true

        // 启动后台线程
        handlerThread = HandlerThread("CaptureThread").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // 缓冲区设为 2，配合 acquireLatestImage 性能最佳
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "FlowCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        // 核心循环：在后台线程抓取
        val captureRunnable = object : Runnable {
            override fun run() {
                if (!isStreaming) return

                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    try {
                        // 1. 处理图像（在后台线程完成像素拷贝和裁剪）
                        val bitmap = processImage(image, width, height)

                        // 2. 发送图像。如果消费者处理慢，旧的会被自动挤掉并触发 recycle
                        if (isRealFrame(bitmap)) {
                            // 只有真实画面才发送
                            _frameChannel.trySend(bitmap)
                        } else {
                            // 如果是黑帧，直接回收，不发送
                            bitmap.recycle()
                            // Log.d(TAG, "检测到初始黑帧，已拦截")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "画面处理异常: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
                // 每 10ms 尝试一次抓取（100FPS 的采样率，实际取决于系统渲染）
                backgroundHandler?.postDelayed(this, 10)
            }
        }
        backgroundHandler?.post(captureRunnable)
    }

    private fun processImage(image: android.media.Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = createBitmap(width + rowPadding / pixelStride, height)
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle() // 立即释放中间的大图
            croppedBitmap
        } else {
            bitmap
        }
    }

    fun stopStreaming() {
        isStreaming = false
        backgroundHandler?.removeCallbacksAndMessages(null)

        // 清理最后一帧残留
        _frameChannel.tryReceive().getOrNull()?.let {
            if (!it.isRecycled) it.recycle()
        }

        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    fun stop() {
        stopStreaming()
        mediaProjection?.stop()
        mediaProjection = null
    }
}