package com.example.floatwindowdemo.utils
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// 存放登录信息的单例
object AuthRepo {
    var sessionToken: String? = null // 存放服务器返回的临时令牌
    var cardCode: String? = null     // 存放用户的卡密
}

object NetworkUtil {
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * 1. 验证卡密 (商用核心)
     */
    suspend fun verifyCardCode(cardCode: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("cardCode", cardCode)
                put("deviceId", Settings.Secure.ANDROID_ID)// 绑定设备
            }

            val request = Request.Builder()
                .url("https://your-api.com/verify") // 你的服务器地址
                .post(json.toString().toRequestBody(JSON))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseDate = JSONObject(response.body?.string() ?: "")
                        // 假设服务器返回 {"status": "success", "msg": "验证通过"}
                        val success = responseDate.optString("status") == "success"
                        AuthRepo.sessionToken = responseDate.optString("token")
                        AuthRepo.cardCode = cardCode
                        Pair(success, responseDate.optString("msg"))
                    } else {
                        Pair(false, "服务器连接失败: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "网络错误: ${e.message}")
            }
        }

    // 心跳校验方法
    suspend fun checkHeartbeat(): Boolean {
        val token = AuthRepo.sessionToken ?: return false

        val json = JSONObject().apply {
            put("token", token)
            put("cardCode", AuthRepo.cardCode)
        }

        // 发送给服务器一个专门的 /heartbeat 接口
        // 服务器如果发现这个 token 不是当前最新的，就返回失败
        // ...
        return true
    }

    /**
     * 2. 检查在线升级
     */
    suspend fun checkUpdate(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://your-api.com/version.json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val data = JSONObject(response.body?.string() ?: "")
                    val latestVersion = data.optString("version")
                    // 如果版本号比当前大，返回下载地址
                    latestVersion
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 3. 发送通用 POST 消息 (比如汇报脚本运行状态)
     */
    suspend fun sendStatusPost(url: String, message: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(message.toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
        }
    }
}