package com.example.floatwindowdemo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.LayoutInflater
import android.widget.Toast
import com.example.floatwindowdemo.utils.DensityUtil
import com.example.floatwindowdemo.R
import com.example.floatwindowdemo.databinding.FloatWindowBinding
import androidx.core.view.isGone

class FloatWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var binding: FloatWindowBinding
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var lastX = 0
    private var lastY = 0
    private var paramX = 0
    private var paramY = 0

    private val NOTIFICATION_CHANNEL_ID = "float_window_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initFloatWindow()
        // Service 启动后的 5秒内 调用 startForeground()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun initFloatWindow() {
        binding = FloatWindowBinding.inflate(LayoutInflater.from(this))

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

            gravity = Gravity.TOP or Gravity.END
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = DensityUtil.dp2px(this@FloatWindowService, 16f)
            y = DensityUtil.dp2px(this@FloatWindowService, 16f)
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
            layoutParams.x = layoutParams.x - dx.toInt()
            layoutParams.y = layoutParams.y + dy.toInt()
            windowManager.updateViewLayout(binding.root, layoutParams)
        }

        binding.btnStartScript.setOnClickListener {
            Toast.makeText(this, "✅ 脚本已开始运行", Toast.LENGTH_SHORT).show()
        }

        binding.btnPauseScript.setOnClickListener {
            Toast.makeText(this, "⏸️ 脚本已暂停", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopScript.setOnClickListener {
            Toast.makeText(this, "❌ 脚本已停止", Toast.LENGTH_SHORT).show()
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
            windowManager.removeView(binding.root)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}