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
import androidx.core.content.ContextCompat
import com.example.floatwindowdemo.R
import com.example.floatwindowdemo.databinding.FloatWindowBinding
import androidx.core.view.isGone
import com.example.floatwindowdemo.MainActivity
import com.example.floatwindowdemo.databinding.LayoutToastBinding
import com.example.floatwindowdemo.manager.ScreenCaptureManager
import com.example.floatwindowdemo.manager.ScriptExecutor
import com.example.floatwindowdemo.utils.ConfigManager

class FloatWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var binding: FloatWindowBinding
    private lateinit var layoutParams: WindowManager.LayoutParams
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

        // 由 Service 统一管理这些“重型”对象的生命周期
        screenCaptureManager = ScreenCaptureManager(this) // 屏幕流失获取
        // Executor脚本执行实例
        scriptExecutor = ScriptExecutor(this, screenCaptureManager) { message ->
            // 当收到消息更新时
            showCustomToast(message)
            // 自动同步按钮文字逻辑 使用 Handler 确保在主线程更新 UI
            toastHandler.post {
                updateUI()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 拿到 MainActivity 传过来的通行证
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("SCREEN_CAPTURE_DATA", Intent::class.java)
        } else {
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
            if (!scriptExecutor.isRunning) {
                // 状态：还没运行 -> 点击开始
                showCustomToast("▶️ 脚本启动")
                // 从本地存储读取当前选中的任务索引
                val taskIndex = ConfigManager.getMainTask(this)
                when (taskIndex) {
                    0 -> {
                        // 拍卖行抢拍
                        scriptExecutor.startAuction()
                    }
                    1 -> {
                        // 调用多角色任务方法
                        scriptExecutor.runYoloTask()
                    }
                    2 -> {
                        // test
                        scriptExecutor.saveScreen()
                    }
                }
                val taskList = listOf("开始游戏","选角", "game")
                // scriptExecutor.execute(taskList)
                // scriptExecutor.showText()
                // scriptExecutor.showBitMap()
            } else {
                // 状态 2：正在运行 -> 点击切换 暂停/恢复
                scriptExecutor.togglePause()
                if (scriptExecutor.isPaused) {
                    showCustomToast("⏸️ 脚本已暂停")
                } else {
                    showCustomToast("▶️ 脚本已恢复")
                }
            }
            updateUI()
        }

        binding.btnSettingScript.setOnClickListener {
            //1. 如果脚本正在运行且没有处于暂停状态，则触发暂停
            if (scriptExecutor.isRunning && !scriptExecutor.isPaused) {
                scriptExecutor.togglePause()
                // 同步更新UI
                updateUI()
            }

            // 2. 跳转回 MainActivity
            val intent = Intent(this, MainActivity::class.java).apply {
                // 关键标志位：
                // FLAG_ACTIVITY_NEW_TASK: Service 跳转 Activity 必须加
                // FLAG_ACTIVITY_SINGLE_TOP: 如果 Activity 已在后台，则直接把它带到前台，而不是重新创建一个
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)

            // 3.自动收起悬浮窗控制面板，让用户视线更清晰
            androidx.transition.TransitionManager.beginDelayedTransition(binding.root as android.view.ViewGroup)
            binding.llControlPanel.visibility = View.GONE
            windowManager.updateViewLayout(binding.root, layoutParams)

            showCustomToast("进入设置")
        }

        binding.btnStopScript.setOnClickListener {
            showCustomToast("脚本已停止")
            scriptExecutor.stop()
            // 同步更新UI
            updateUI()
        }

        binding.btnCloseScript.setOnClickListener {
            showCustomToast("退出脚本")
            scriptExecutor.stop()
            // 发送广播通知 MainActivity 关闭
            val intent = Intent("com.example.floatwindowdemo.ACTION_EXIT")
            sendBroadcast(intent)
            // 停止 Service 本身
            stopSelf()
        }
    }

    // 更新UI显示
    private fun updateUI(){
        if (!scriptExecutor.isRunning) {
            // 状态: 等待开始
            binding.btnStartScript.text = "开始"
            val pauseIcon = ContextCompat.getDrawable(this, R.drawable.ic_play)
            binding.btnStartScript.setCompoundDrawablesWithIntrinsicBounds(null, pauseIcon, null, null)
        } else {
            if (scriptExecutor.isPaused){
                // 等待恢复
                binding.btnStartScript.text = "恢复"
                val pauseIcon = ContextCompat.getDrawable(this, R.drawable.ic_pause)
                binding.btnStartScript.setCompoundDrawablesWithIntrinsicBounds(null, pauseIcon, null, null)
            }else{
                // 正在运行
                binding.btnStartScript.text = "暂停"
                val pauseIcon = ContextCompat.getDrawable(this, R.drawable.ic_resume)
                binding.btnStartScript.setCompoundDrawablesWithIntrinsicBounds(null, pauseIcon, null, null)
            }

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

        // 释放屏幕采集会话，防止内存泄漏和通知栏残留
        if (::screenCaptureManager.isInitialized) {
            screenCaptureManager.stop()
        }

        // 停止前台通知（适配 API 33+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // 彻底杀掉进程（延迟 300ms 以确保 UI 清理完毕）
        toastHandler.postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 300)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}