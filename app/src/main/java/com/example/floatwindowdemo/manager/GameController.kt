package com.example.floatwindowdemo.manager

import com.example.floatwindowdemo.utils.NetworkUtil
import org.json.JSONObject

object GameController{
    /***
     * 发送喵提醒
     * id: 用户id
     * text：提醒消息
     */
    suspend fun postMiao(id: String, text: String): Boolean{
        // 直接构建 JSON 对象
        val params = JSONObject().apply {
            put("id", id)
            put("text", text)
        }
        // 异步发送
        return NetworkUtil.sendPost("https://miaotixing.com/trigger", params)
    }

}