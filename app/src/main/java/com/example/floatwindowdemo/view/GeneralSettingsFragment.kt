package com.example.floatwindowdemo.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.floatwindowdemo.R
import com.example.floatwindowdemo.databinding.FragmentGeneralSettingsBinding
import com.example.floatwindowdemo.databinding.ItemRoleConfigBinding
import com.example.floatwindowdemo.utils.AuctionConfig
import com.example.floatwindowdemo.utils.ConfigManager
import com.example.floatwindowdemo.utils.RoleData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GeneralSettingsFragment : Fragment() {
    private var _binding: FragmentGeneralSettingsBinding? = null
    private val binding get() = _binding!!

    private var expandedView: View? = null // 当前展开的view，用于手风琴效果
    private val settingGroups by lazy {
        listOf(
            binding.groupAuctionSettings,   // Index 0，拍卖行
            binding.groupMultiRoleSettings  // Index 1，多角色
            // 后面新增任务 3，直接在这里继续添加 binding.groupNewTaskSettings
        )
    }
    companion object { // 静态常量
        private val MAIN_TASKS = arrayOf("拍卖行抢拍", "多角色任务")
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

            if (count in 1..22) {
                generateRoleList(count)
            } else {
                Toast.makeText(requireContext(), "请输入1-22之间的数字", Toast.LENGTH_SHORT).show()
            }
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
        ConfigManager.saveAuctionConfig(context, AuctionConfig(price, quantity))

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
        expandedView = null        // 重置展开项

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

        // 1. 初始化显示
        itemBinding.tvRoleName.text = "角色 $index"
        // 默认收起详情
        itemBinding.layoutTasksContent.visibility = View.GONE

        // 2. 点击标题栏切换展开/收起 (手风琴效果)
        itemBinding.layoutHeader.setOnClickListener {
            val isExpanding = itemBinding.layoutTasksContent.visibility == View.GONE

            // 使用 TransitionManager 增加丝滑动画
            androidx.transition.TransitionManager.beginDelayedTransition(container)

            if (isExpanding) {
                // 收起之前展开的
                expandedView?.findViewById<View>(R.id.layout_tasks_content)?.visibility = View.GONE
                // 展开当前的
                itemBinding.layoutTasksContent.visibility = View.VISIBLE
                expandedView = itemBinding.root
            } else {
                // 点击已展开的项则收起
                itemBinding.layoutTasksContent.visibility = View.GONE
                expandedView = null
            }
        }

        // 3. 原有的开关逻辑 (控制是否启用，不影响手动展开/收起)
        itemBinding.switchRoleEnable.setOnCheckedChangeListener { _, isChecked ->
            itemBinding.tvRoleName.alpha = if (isChecked) 1.0f else 0.5f
        }

        // 4. 删除按钮 (针对批量生成模式，可保留用于微调)
        itemBinding.btnDeleteRole.setOnClickListener {
            container.removeView(itemBinding.root)
            if (expandedView == itemBinding.root) expandedView = null
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
            itemBinding.cbDaily.isChecked = data.daily
            itemBinding.cbBoss.isChecked = data.boss
            itemBinding.cbDecompose.isChecked = data.decompose
            itemBinding.cbMail.isChecked = data.mail

            // 还原视觉效果（因为初始化时 Listener 可能不会自动触发 alpha 变化）
            itemBinding.tvRoleName.alpha = if (data.isEnabled) 1.0f else 0.5f
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
                daily = itemBinding.cbDaily.isChecked,
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
