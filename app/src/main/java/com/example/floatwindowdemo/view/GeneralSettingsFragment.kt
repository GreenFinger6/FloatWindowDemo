package com.example.floatwindowdemo.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import com.example.floatwindowdemo.databinding.FragmentGeneralSettingsBinding
import com.example.floatwindowdemo.utils.AuctionConfig
import com.example.floatwindowdemo.utils.ConfigManager

class GeneralSettingsFragment : Fragment() {
    private var _binding: FragmentGeneralSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneralSettingsBinding.inflate(inflater, container, false)
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

        // 初始化回显（从本地读取已保存的配置）
        loadSettings()
    }

    /**
     * 加载本地保存的配置
     */
    fun loadSettings() {
        // 此时直接返回，不执行后续 UI 读取逻辑，避免崩溃
        if(_binding == null) return
        // 拍卖行配置
        val auctionConfig = ConfigManager.getAuctionConfig(requireContext())
        binding.editMaxPrice.setText(auctionConfig.maxPrice.toString())
        binding.editMaxQuantity.setText(auctionConfig.maxQuantity.toString())
    }

    /**
     * 读取 UI 上的值并保存到本地
     */
    fun saveSettings() {
        // 此时直接返回，不执行后续 UI 读取逻辑，避免崩溃
        if(_binding == null) return
        val price = binding.editMaxPrice.text.toString().toLongOrNull() ?: 0L
        val quantity = binding.editMaxQuantity.text.toString().toLongOrNull() ?: 0L
        ConfigManager.saveAuctionConfig(requireContext(), AuctionConfig(price, quantity))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
