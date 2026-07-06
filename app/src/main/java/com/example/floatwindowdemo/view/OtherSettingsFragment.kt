package com.example.floatwindowdemo.view

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.floatwindowdemo.databinding.FragmentOtherSettingsBinding
import com.example.floatwindowdemo.utils.NetworkUtil
import com.example.floatwindowdemo.utils.installApk
import kotlinx.coroutines.launch
import java.io.File

class OtherSettingsFragment : Fragment() {
    private var _binding: FragmentOtherSettingsBinding? = null
    private val binding get() = _binding!!

    // 伴生对象，等价于static
    companion object {
        // 使用 const 修饰，性能更高（编译时常量）
        private const val UPDATE_APK_NAME = "update.apk"
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtherSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 处理开关
        binding.switch1.setOnCheckedChangeListener { _, isChecked ->
            // 保存设置或通知 Service
        }

        // 2. 处理下拉框 (Material AutoCompleteTextView)
        val items = arrayOf("极慢", "正常", "极快")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        (binding.spinnerSpeed as? AutoCompleteTextView)?.setAdapter(adapter)

        // 3. 处理滑块
        binding.sliderAlpha.addOnChangeListener { slider, value, fromUser ->
            // 这里 value 是 0.0 到 1.0
        }

        // 检查更新按钮绑定监听器
        initUpdateLogic()

        // 初始化回显（从本地读取已保存的配置）
        loadSettings()
    }

    /**
     * 加载本地保存的配置
     */
    fun loadSettings() {
        // todo
        // 此时直接返回，不执行后续 UI 读取逻辑，避免崩溃
        if(_binding == null) return
    }

    /**
     * 读取 UI 上的值并保存到本地
     */
    fun saveSettings() {
        // todo
        // 此时直接返回，不执行后续 UI 读取逻辑，避免崩溃
        if(_binding == null) return
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // 检查更新按钮点击逻辑
    private fun initUpdateLogic() {
        // 显示当前版本号
        val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        binding.tvCurrentVersion.text = "当前版本: ${packageInfo.versionName}"

        binding.layoutCheckUpdate.setOnClickListener {
            lifecycleScope.launch {
                // 1. 获取服务器数据
                val updateInfo = NetworkUtil.checkUpdate() // 之前定义的获取 JSON 的方法
                if (updateInfo == null) {
                    Toast.makeText(context, "检查更新失败，请检查网络", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val serverVersionCode = updateInfo.optInt("versionCode")
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }

                if (serverVersionCode > currentVersionCode) {
                    // 2. 弹出更新对话框
                    showUpdateDialog(
                        updateInfo.optString("versionName"),
                        updateInfo.optString("updateMsg"),
                        updateInfo.optString("downloadUrl"),
                        serverVersionCode
                    )
                } else {
                    Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(versionName: String, msg: String, url: String, serverVersionCode: Int) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("发现新版本 $versionName")
            .setMessage(msg)
            .setPositiveButton("立即更新") { _, _ ->
                startDownload(url,serverVersionCode)
            }
            .setNegativeButton("以后再说", null)
            .show()
    }

    private fun startDownload(url: String, serverVersionCode: Int) {
        val apkFile = File(requireContext().externalCacheDir, UPDATE_APK_NAME)

        // --- 判断本地是否存在已下载好的最新包 ---
        if (apkFile.exists()) {
            try {
                val archiveInfo = requireContext().packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                val localFileCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    archiveInfo?.longVersionCode
                } else {
                    archiveInfo?.versionCode?.toLong()
                }

                // 如果本地文件版本号 等于 服务器版本号，直接安装
                if (localFileCode == serverVersionCode.toLong()) {
                    android.util.Log.d("Update", "检测到本地已存在最新安装包，跳过下载")
                    installApk(requireContext(), apkFile)
                    return
                }
            } catch (e: Exception) {
                // 如果解析失败（文件损坏等），则删除旧文件重新下载
                apkFile.delete()
            }
        }
        // ------------------------------------------

        // 如果没有命中上面的 return，则继续执行下载逻辑
        // 创建一个进度条对话框
        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setTitle("正在下载更新...")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            // 设置右下角显示格式为 "当前大小/总大小"  %1d 代表当前已下载的值，%2d 代表 setMax 设置的总值
            setProgressNumberFormat("%1d MB / %2d MB")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val apkFile = File(requireContext().externalCacheDir, UPDATE_APK_NAME)
            // 它回调当前字节数和总字节数
            val success = NetworkUtil.downloadApk(url, apkFile) { downloaded, total ->
                // 将字节转为 MB 显示
                val currentKB = (downloaded / (1024*1024)).toInt()
                val totalKB = (total / (1024*1024)).toInt()
                progressDialog.max = totalKB
                progressDialog.progress = currentKB
            }

            progressDialog.dismiss()
            if (success) {
                // 之前定义的安装逻辑
                installApk(requireContext(), apkFile)
            } else {
                Toast.makeText(context, "下载失败，请检查网络", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
