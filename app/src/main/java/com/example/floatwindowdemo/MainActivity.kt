package com.example.floatwindowdemo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.floatwindowdemo.databinding.ActivityMainBinding
import com.example.floatwindowdemo.service.AutomationService
import com.example.floatwindowdemo.service.FloatWindowService
import com.example.floatwindowdemo.view.GeneralSettingsFragment
import com.example.floatwindowdemo.view.OtherSettingsFragment
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.text.equals

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    // 权限请求码
    private val REQUEST_FLOAT_WINDOW_PERMISSION = 1001
    // 页签实例
    private val generalSettingsFragment = GeneralSettingsFragment()
    private val otherSettingsFragment = OtherSettingsFragment()
    // 退出广播接收器
    private val exitReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finishAffinity() // 关闭当前任务栈的所有 Activity
        }
    }

    // 注册屏幕采集请求的回调
    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // 3. 只有屏幕采集也成功了，才真正开启 Service
            startFloatWindowService(result.data!!)
        } else {
            // 屏幕采集被拒绝，不开启 Service
            Toast.makeText(this, "必须授权屏幕采集才能使用此功能", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 接受关闭广播，用于退出程序
        ContextCompat.registerReceiver(
            this,    exitReceiver,
            android.content.IntentFilter("com.example.floatwindowdemo.ACTION_EXIT"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 设置 androidx . viewpager2 . widget . ViewPager2 的适配器
        val adapter = SettingPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 1 // 预加载并保留相邻的 1 个页面

        // 将 TabLayout 与 ViewPager2 关联
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "通用设置"
                1 -> "其他设置"
                else -> null
            }
        }.attach()

        // 保存按钮逻辑
        binding.btnSaveConfig.setOnClickListener {
            generalSettingsFragment.saveSettings()
            otherSettingsFragment.saveSettings()
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
        }
        // 开启悬浮窗按钮
        binding.btnStartFloat.setOnClickListener {
            handleStartFlow()
        }
        // 关闭悬浮窗按钮
        binding.btnStopFloat.setOnClickListener {
            stopFloatWindowService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注销广播接收器，防止内存泄漏
        try {
            unregisterReceiver(exitReceiver)
        } catch (e: Exception) {
            // 防止重复注销报错
        }
    }

    // 内部适配器类
    inner class SettingPagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2 // 页签数量
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> generalSettingsFragment // 通用设置类
                else -> otherSettingsFragment// 其他设置
            }
        }
    }

    // 统一的处理流程
    private fun handleStartFlow() {

        // 检查悬浮窗权限
        if (!checkFloatWindowPermission()) {
            requestFloatWindowPermission()
            return
        }

        // 检查无障碍权限
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请开启无障碍服务以支持自动点击", Toast.LENGTH_LONG).show()
            requestAccessibilityPermission()
            return
        }

        // 如果已经在悬浮窗已开启，就不需要重复走申请流程
        if (isServiceRunning(FloatWindowService::class.java)) {
            Toast.makeText(this, "服务已在运行中", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
            return
        }

        // 申请屏幕采集权限并启动 Service
        requestScreenCapturePermission()
    }

    // 辅助方法：检查服务是否在运行
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    // 检查悬浮窗权限
    private fun checkFloatWindowPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            // 安卓6.0以下不需要申请
            true
        }
    }

    // 申请悬浮窗权限
    private fun requestFloatWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_FLOAT_WINDOW_PERMISSION)
        }
    }

    // 发起屏幕采集权限请求
    private fun requestScreenCapturePermission() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestScreenCapture.launch(manager.createScreenCaptureIntent())
    }

    // 处理悬浮窗权限申请返回的结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FLOAT_WINDOW_PERMISSION) {
            if (checkFloatWindowPermission()) {
                // 悬浮窗权限刚拿到了，立即进入下一步：申请屏幕权限
                requestScreenCapturePermission()
            } else {
                Toast.makeText(this, "未获得悬浮窗权限，无法开启", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 启动悬浮窗服务
    private fun startFloatWindowService(captureIntent: Intent) {
        val intent = Intent(this, FloatWindowService::class.java).apply {
            // 将“屏幕获取通行证”放入 Extra 传给 Service
            putExtra("SCREEN_CAPTURE_DATA", captureIntent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "服务已开启", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    // 停止悬浮窗服务
    private fun stopFloatWindowService() {
        val intent = Intent(this, FloatWindowService::class.java)
        stopService(intent)
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
    }

    // 检查无障碍服务是否开启
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, AutomationService::class.java)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        if (enabledServices == null) return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName.flattenToString(), ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    // 跳转到系统设置页面
    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}