package com.example.floatwindowdemo.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.LayoutInflater
import com.example.floatwindowdemo.utils.DensityUtil
import com.example.floatwindowdemo.R
import com.example.floatwindowdemo.databinding.FloatWindowBinding
import androidx.core.view.isGone
import com.example.floatwindowdemo.databinding.LayoutToastBinding
import com.example.floatwindowdemo.manager.OcrManager
import com.example.floatwindowdemo.manager.ScreenCaptureManager
import com.example.floatwindowdemo.manager.ScriptExecutor

class FloatWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var binding: FloatWindowBinding
    private lateinit var layoutParams: WindowManager.LayoutParams
    // ocr识别
    private lateinit var ocrManager: OcrManager
    // 屏幕获取
    private lateinit var screenCaptureManager: ScreenCaptureManager
    // 任务执行
    private lateinit var scriptExecutor: ScriptExecutor

    // Toast 专用变量，允许为空防止加载失败影响悬浮窗
    private var toastView: View? = null
    private var toastBinding: LayoutToastBinding? = null // 假设你启用了 ViewBinding
    private val toastHandler = Handler(Looper.getMainLooper())

    private var lastX = 0
    private var lastY = 0
    private var paramX = 0
    private var paramY = 0

    private val NOTIFICATION_CHANNEL_ID = "float_window_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        setTheme(R.style.Theme_FloatWindowDemo)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 1. 初始化悬浮球和面板
        initFloatWindow()

        // 2. 初始化提示窗口
        initToastWindow()

        // Service 启动后的 5秒内 调用 startForeground()
        startForeground(NOTIFICATION_ID, createNotification())

        // 创建相关模型单例
        ocrManager = OcrManager(this)
        screenCaptureManager = ScreenCaptureManager(this)
        // Executor脚本执行实例
        scriptExecutor = ScriptExecutor(screenCaptureManager, ocrManager) { message ->
            showCustomToast(message)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 拿到 MainActivity 传过来的通行证
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("SCREEN_CAPTURE_DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("SCREEN_CAPTURE_DATA")
        }
        if (data != null) {
            // 传入 RESULT_OK 和 令牌
            screenCaptureManager.init(Activity.RESULT_OK, data)
        }
        return START_STICKY
    }

    private fun initFloatWindow() {
        binding = FloatWindowBinding.inflate(LayoutInflater.from(this))

        // 获取屏幕宽高
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

            format = PixelFormat.RGBA_8888

            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT

            // 获取悬浮球大小，并计算中点
            val ballSize = resources.getDimensionPixelSize(R.dimen.float_size)
            x = (screenWidth - ballSize) / 2
            y = (screenHeight - ballSize) / 2
        }

        windowManager.addView(binding.root, layoutParams)

        // 悬浮球点击事件
        binding.ivFloatBall.setOnClickListener {
            // 1. 开启延迟过渡动画：系统会自动监听接下来对布局的改动并添加动画
            // 这个方法会让布局变化时带上淡入淡出和位移效果
            androidx.transition.TransitionManager.beginDelayedTransition(
                binding.root as android.view.ViewGroup,
                androidx.transition.AutoTransition().apply {
                    duration = 300 // 动画时长 300 毫秒
                }
            )

            // 2. 修改可见性
            if (binding.llControlPanel.isGone) {
                binding.llControlPanel.visibility = View.VISIBLE
            } else {
                binding.llControlPanel.visibility = View.GONE
            }

            // 3. 关键点：因为宽度变了，动画过程中需要告诉 WindowManager 重新布局
            // 某些情况下系统不一定会自动重绘窗口，加上这一句可以确保窗口大小能跟随动画延展
            windowManager.updateViewLayout(binding.root, layoutParams)
        }

        // 悬浮球拖拽事件
        binding.ivFloatBall.onDragListener = { dx, dy ->
            layoutParams.x = layoutParams.x + dx.toInt()
            layoutParams.y = layoutParams.y + dy.toInt()
            windowManager.updateViewLayout(binding.root, layoutParams)
        }

        binding.btnStartScript.setOnClickListener {
            // 定义你的任务列表
            scriptExecutor.showText();
        }

        binding.btnPauseScript.setOnClickListener {
            showCustomToast("⏸️ 脚本已暂停")
        }

        binding.btnStopScript.setOnClickListener {
            showCustomToast("❌ 脚本已停止")
            scriptExecutor.stop()
        }
    }

    private fun initToastWindow() {
        // 使用 ViewBinding 加载layout_toast.xml
        toastBinding = LayoutToastBinding.inflate(LayoutInflater.from(this))

        val toastParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // 关键点：FLAG_NOT_FOCUSABLE 不抢占焦点
            // FLAG_NOT_TOUCHABLE 确保点击穿透，用户点到提示框位置依然能操作底下的应用
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        toastView = toastBinding?.root
        toastView?.visibility = View.GONE // 初始隐藏
        windowManager.addView(toastView, toastParams)
    }

    // 后台运行任务栏显示信息
    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗控制面板后台运行服务"
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("悬浮窗控制面板")
            .setContentText("正在后台运行")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    // 后台运行时消息显示方法
    private fun showCustomToast(message: String) {
        // 1. 取消之前的隐藏任务
        toastHandler.removeCallbacksAndMessages(null)

        val view = toastView ?: return
        val tvContent = toastBinding?.tvToastContent ?: return

        tvContent.text = message

        // 2. 显示动画：淡入 + 从上方滑入
        if (view.visibility == View.GONE) {
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.translationY = -50f // 初始向上偏移
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }

        // 3. 2.5秒后自动隐藏动画
        toastHandler.postDelayed({
            view.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(300)
                .withEndAction {
                    view.visibility = View.GONE
                }
                .start()
        }, 2500)
    }

    override fun onDestroy() {
        super.onDestroy()

        // 销毁时停止正在运行的脚本
        if (::scriptExecutor.isInitialized) {
            scriptExecutor.stop()
        }

        // 移除悬浮球和面板窗口
        if (::binding.isInitialized) {
            try {
                windowManager.removeView(binding.root)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 移除独立的提示框窗口
        toastView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 移除所有尚未执行的定时隐藏任务，防止服务销毁后还尝试操作 UI
        toastHandler.removeCallbacksAndMessages(null)

        // 释放 ML Kit 引擎和屏幕采集会话，防止内存泄漏和通知栏残留
        if (::ocrManager.isInitialized) {
            ocrManager.stop()
        }
        if (::screenCaptureManager.isInitialized) {
            screenCaptureManager.stop()
        }

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}