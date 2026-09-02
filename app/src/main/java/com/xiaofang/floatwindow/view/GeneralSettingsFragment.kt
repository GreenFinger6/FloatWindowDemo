package com.xiaofang.floatwindow.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.xiaofang.floatwindow.databinding.FragmentGeneralSettingsBinding
import com.xiaofang.floatwindow.databinding.ItemRoleConfigBinding
import com.xiaofang.floatwindow.utils.AuctionConfig
import com.xiaofang.floatwindow.utils.ConfigManager
import com.xiaofang.floatwindow.utils.RoleData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GeneralSettingsFragment : Fragment() {
    private var _binding: FragmentGeneralSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingGroups by lazy {
        listOf(
            binding.groupAuctionSettings,   // Index 0，拍卖行
            binding.groupMultiRoleSettings  // Index 1，多角色
            // 后面新增任务 3，直接在这里继续添加 binding.groupNewTaskSettings
        )
    }
    companion object { // 静态常量
        private val MAIN_TASKS = arrayOf("拍卖行抢拍", "多角色任务", "测试任务")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneralSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, MAIN_TASKS)
        binding.spinnerMainTask.setAdapter(adapter)

        binding.spinnerMainTask.setOnItemClickListener { _, _, position, _ ->
            updateTaskUI(position)
        }

        // 绑定生成按钮
        binding.btnGenerateRoles.setOnClickListener {
            val countStr = binding.editRoleCount.text.toString()
            val count = countStr.toIntOrNull() ?: 0

            if (count in 1..24) {
                generateRoleList(count)
            } else {
                Toast.makeText(requireContext(), "请输入1-24之间的数字", Toast.LENGTH_SHORT).show()
            }
        }

        // 绑定同步配置按钮
        binding.btnSyncConfig.setOnClickListener {
            syncFirstRoleToAll()
        }

        // 初始化回显配置（从本地读取已保存的配置）
        loadSettings()
    }

    /**
     * 加载本地保存的配置
     */
    fun loadSettings() {
        // 此时直接返回，不执行后续 UI 读取逻辑，避免崩溃
        if(_binding == null) return
        val context = requireContext()

        // 当前主任务
        val taskIndex = ConfigManager.getMainTask(context)
        binding.spinnerMainTask.setText(MAIN_TASKS[taskIndex], false)

        // 拍卖行配置
        val auctionConfig = ConfigManager.getAuctionConfig(context)
        binding.editMaxPrice.setText(auctionConfig.maxPrice.toString())
        binding.editMaxQuantity.setText(auctionConfig.maxQuantity.toString())
        binding.switchGreedyMode.isChecked = auctionConfig.isGreedy

        // 根据主任务索引切换UI面板
        updateTaskUI(taskIndex)

        // 多角色任务需要手动创建容器并加载配置
        loadRoleDetails()
    }

    /**
     * 读取 UI 上的值并保存到本地
     */
    fun saveSettings() {
        // 此时直接返回，不执行后续 UI 读取逻辑，避免崩溃
        if(_binding == null) return
        val context = requireContext()

        // 保存当前主任务索引
        val taskName = binding.spinnerMainTask.text.toString()
        val taskIndex = MAIN_TASKS.indexOf(taskName).coerceAtLeast(0)
        ConfigManager.saveMainTask(context, taskIndex)

        // 拍卖行配置
        val price = binding.editMaxPrice.text.toString().toLongOrNull() ?: 0L
        val quantity = binding.editMaxQuantity.text.toString().toLongOrNull() ?: 0L
        val isGreedy = binding.switchGreedyMode.isChecked
        ConfigManager.saveAuctionConfig(context, AuctionConfig(price, quantity, isGreedy))

        // 保存角色配置
        saveAllRolesDetails()
    }

    /**
     * 核心优化：统一切换 UI 面板的方法
     */
    private fun updateTaskUI(taskIndex: Int) {
        settingGroups.forEachIndexed { index, view ->
            view.visibility = if (index == taskIndex) View.VISIBLE else View.GONE
        }
    }

    /**
     * 批量生成角色列表
     */
    private fun generateRoleList(count: Int) {
        val container = binding.containerRoles
        container.removeAllViews() // 清空旧的

        for (i in 1..count) {
            addRoleItem(i)
        }
    }

    /**
     * 新增角色配置项
     */
    private fun addRoleItem(index: Int) {
        val container = binding.containerRoles
        val itemBinding = ItemRoleConfigBinding.inflate(layoutInflater, container, false)

        // 1. 初始化显示：将角色名设置给勾选框
        itemBinding.switchRoleEnable.text = "角色 $index"

        // 2. 初始化下拉框选项
        val tasks = arrayOf("深渊秘境", "老深渊")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tasks)
        itemBinding.spinnerDailyTask.setAdapter(adapter)
        // 默认选中第一个
        itemBinding.spinnerDailyTask.setText(tasks[0], false)
        
        // 默认勾选其他功能
        itemBinding.cbBoss.isChecked = true
        itemBinding.cbDecompose.isChecked = true
        itemBinding.cbMail.isChecked = true

        // 3. 开关逻辑 (控制是否启用)
        itemBinding.switchRoleEnable.setOnCheckedChangeListener { _, isChecked ->
            itemBinding.switchRoleEnable.alpha = if (isChecked) 1.0f else 0.5f
            // 灰掉所有功能控件表示禁用
            itemBinding.spinnerDailyTask.isEnabled = isChecked
            itemBinding.cbBoss.isEnabled = isChecked
            itemBinding.cbDecompose.isEnabled = isChecked
            itemBinding.cbMail.isEnabled = isChecked
        }

        container.addView(itemBinding.root)
    }

    private fun loadRoleDetails() {
        val json = ConfigManager.getRoleDataJson(requireContext())
        if (json.isEmpty() || json == "[]") return

        // 1. 解析 JSON 得到 List<RoleData>
         val roleList: List<RoleData> = Gson().fromJson(json, object : TypeToken<List<RoleData>>() {}.type)

        // 2. 更新输入框显示的数量
        binding.editRoleCount.setText(roleList.size.toString())

        // 3. 批量生成空 View
        generateRoleList(roleList.size)

        // 4. 遍历生成的 View，还原勾选状态
        val container = binding.containerRoles
        for (i in 0 until container.childCount) {
            val itemView = container.getChildAt(i)
            val itemBinding = ItemRoleConfigBinding.bind(itemView)
            val data = roleList[i]

            // 还原各控件状态
            itemBinding.switchRoleEnable.isChecked = data.isEnabled
            itemBinding.switchRoleEnable.text = "角色 ${i + 1}"
            itemBinding.spinnerDailyTask.setText(data.dailyTask, false)
            itemBinding.cbBoss.isChecked = data.boss
            itemBinding.cbDecompose.isChecked = data.decompose
            itemBinding.cbMail.isChecked = data.mail

            // 还原视觉效果并联动禁用状态
            itemBinding.switchRoleEnable.alpha = if (data.isEnabled) 1.0f else 0.5f
            itemBinding.spinnerDailyTask.isEnabled = data.isEnabled
            itemBinding.cbBoss.isEnabled = data.isEnabled
            itemBinding.cbDecompose.isEnabled = data.isEnabled
            itemBinding.cbMail.isEnabled = data.isEnabled
        }
    }

    private fun saveAllRolesDetails() {
        val container = binding.containerRoles
        val roleList = mutableListOf<RoleData>()

        // 遍历容器中的所有子 View
        for (i in 0 until container.childCount) {
            val itemView = container.getChildAt(i)
            // 使用 ViewBinding 绑定这个已经存在的 View
            val itemBinding = ItemRoleConfigBinding.bind(itemView)

            // 收集该行数据
            val data = RoleData(
                isEnabled = itemBinding.switchRoleEnable.isChecked,
                dailyTask = itemBinding.spinnerDailyTask.text.toString(),
                boss = itemBinding.cbBoss.isChecked,
                decompose = itemBinding.cbDecompose.isChecked,
                mail = itemBinding.cbMail.isChecked
            )
            roleList.add(data)
        }

        // 将对象列表转为 JSON (如果你还没引入 Gson 库，建议在 build.gradle 加上)
         val json = Gson().toJson(roleList)
         ConfigManager.saveRoleDataJson(requireContext(), json)
    }

    /**
     * 将角色1的配置同步给所有角色
     */
    private fun syncFirstRoleToAll() {
        val container = binding.containerRoles
        if (container.childCount < 2) {
            Toast.makeText(requireContext(), "请生成至少两个角色", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 获取第一个角色的配置
        val firstView = container.getChildAt(0)
        val firstBinding = ItemRoleConfigBinding.bind(firstView)
        
        val isEnabled = firstBinding.switchRoleEnable.isChecked
        val dailyTask = firstBinding.spinnerDailyTask.text.toString()
        val boss = firstBinding.cbBoss.isChecked
        val decompose = firstBinding.cbDecompose.isChecked
        val mail = firstBinding.cbMail.isChecked

        // 2. 遍历并同步给其他角色
        for (i in 1 until container.childCount) {
            val itemView = container.getChildAt(i)
            val itemBinding = ItemRoleConfigBinding.bind(itemView)

            // 设置状态
            itemBinding.switchRoleEnable.isChecked = isEnabled
            itemBinding.spinnerDailyTask.setText(dailyTask, false)
            itemBinding.cbBoss.isChecked = boss
            itemBinding.cbDecompose.isChecked = decompose
            itemBinding.cbMail.isChecked = mail

            // 触发 UI 禁用/启用联动 (手动触发 listener 逻辑)
            itemBinding.switchRoleEnable.alpha = if (isEnabled) 1.0f else 0.5f
            itemBinding.spinnerDailyTask.isEnabled = isEnabled
            itemBinding.cbBoss.isEnabled = isEnabled
            itemBinding.cbDecompose.isEnabled = isEnabled
            itemBinding.cbMail.isEnabled = isEnabled
        }

        Toast.makeText(requireContext(), "已将角色1的配置同步至所有角色", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
