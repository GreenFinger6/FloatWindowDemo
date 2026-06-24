package com.example.floatwindowdemo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.floatwindowdemo.databinding.ActivityMainBinding
import com.example.floatwindowdemo.service.FloatWindowService
import com.example.floatwindowdemo.view.GeneralSettingsFragment
import com.example.floatwindowdemo.view.OtherSettingsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // 权限请求码
    private val REQUEST_FLOAT_WINDOW_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置 androidx . viewpager2 . widget . ViewPager2 的适配器
        val adapter = SettingPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // 将 TabLayout 与 ViewPager2 关联
        com.google.android.material.tabs.TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "通用设置"
                1 -> "其他设置"
                else -> null
            }
        }.attach()

        // 开启悬浮窗按钮
        binding.btnStartFloat.setOnClickListener {
            if (checkFloatWindowPermission()) {
                startFloatWindowService()
            } else {
                requestFloatWindowPermission()
            }
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

    // 处理权限申请结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FLOAT_WINDOW_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
                    startFloatWindowService()
                } else {
                    Toast.makeText(this, "悬浮窗权限被拒绝", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 启动悬浮窗服务
    private fun startFloatWindowService() {
        val intent = Intent(this, FloatWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "悬浮窗已开启", Toast.LENGTH_SHORT).show()
        // 启动服务后返回桌面，模拟在其他应用上显示
        moveTaskToBack(true)
    }

    // 停止悬浮窗服务
    private fun stopFloatWindowService() {
        val intent = Intent(this, FloatWindowService::class.java)
        stopService(intent)
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
    }
}