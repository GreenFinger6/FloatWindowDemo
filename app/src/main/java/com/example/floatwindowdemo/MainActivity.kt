package com.example.floatwindowdemo

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.floatwindowdemo.databinding.ActivityMainBinding
import com.example.floatwindowdemo.service.FloatWindowService
import com.example.floatwindowdemo.view.GeneralSettingsFragment
import com.example.floatwindowdemo.view.OtherSettingsFragment
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // 权限请求码
    private val REQUEST_FLOAT_WINDOW_PERMISSION = 1001

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

        // 设置 androidx . viewpager2 . widget . ViewPager2 的适配器
        val adapter = SettingPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // 将 TabLayout 与 ViewPager2 关联
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "通用设置"
                1 -> "其他设置"
                else -> null
            }
        }.attach()

        // 开启悬浮窗按钮
        binding.btnStartFloat.setOnClickListener {
            handleStartFlow()
        }

        // 关闭悬浮窗按钮
        binding.btnStopFloat.setOnClickListener {
            stopFloatWindowService()
        }
    }

    // 内部适配器类
    inner class SettingPagerAdapter(fa: FragmentActivity) :
        FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2 // 页签数量
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> GeneralSettingsFragment() // 你定义的通用设置类
                else -> OtherSettingsFragment() // 其他设置
            }
        }
    }

    // 统一的处理流程
    private fun handleStartFlow() {
        if (!checkFloatWindowPermission()) {
            // 1. 没悬浮窗权限，先去申请
            requestFloatWindowPermission()
            return
        }

        // 如果已经在悬浮窗已开启，就不需要重复走申请流程
        if (isServiceRunning(FloatWindowService::class.java)) {
            Toast.makeText(this, "服务已在运行中", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
            return
        }

        // 2. 走到这里说明悬浮窗权限已有，去申请屏幕采集权限
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
            // 将“通行证”放入 Extra 传给 Service
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
}