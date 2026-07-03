package com.example.floatwindowdemo.utils
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

// 存放登录信息的单例
object AuthRepo {
    var sessionToken: String? = null // 存放服务器返回的临时令牌
    var cardCode: String? = null     // 存放用户的卡密
}

object NetworkUtil {
    private const val TAG = "NetworkUtil"

    // --- 统一管理服务器地址 ---
    private const val BASE_URL = "https://your-api.com"
    private const val URL_VERIFY = "$BASE_URL/verify"
    private const val URL_CHECK_UPDATE = "https://raw.githubusercontent.com/GreenFinger6/FloatWindowDemo/refs/heads/main/update_config.json"
    private const val URL_HEARTBEAT = "$BASE_URL/heartbeat"
    // -----------------------

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // client 初始化
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }


    /**
     * 【核心抽象方法】
     * 统一处理：线程切换、执行请求、检查状态、异常捕获、自动关闭资源
     */
    private suspend fun <T> executeRequest(
        request: Request,
        parser: (Response) -> T
    ): T? = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    parser(response)
                } else {
                    Log.w(TAG, "请求失败: ${request.url} 状态码: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "网络异常: ${request.url} -> ${e.message}")
            null
        }
    }

    /**
     * 1. 验证卡密
     */
    suspend fun verifyCardCode(cardCode: String): Pair<Boolean, String> {
        val json = JSONObject().apply {
            put("cardCode", cardCode)
            put("deviceId", Settings.Secure.ANDROID_ID)
        }
        val request = Request.Builder()
            .url(URL_VERIFY)
            .post(json.toString().toRequestBody(JSON))
            .build()

        // 使用通用方法解析
        val result = executeRequest(request) { response ->
            val data = JSONObject(response.body?.string() ?: "")
            val success = data.optString("status") == "success"
            if (success) {
                AuthRepo.sessionToken = data.optString("token")
                AuthRepo.cardCode = cardCode
            }
            Pair(success, data.optString("msg", "未知响应"))
        }
        return result ?: Pair(false, "连接服务器失败")
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
    suspend fun checkUpdate(): JSONObject? {
        val request = Request.Builder()
            .url(URL_CHECK_UPDATE)
            .get() // 默认就是 GET，显式写出来更清晰
            .build()

        return executeRequest(request) { response ->
            JSONObject(response.body?.string() ?: "")
        }
    }

    /**
     * 3. 发送通用 POST 消息
     */
    suspend fun sendStatusPost(url: String, message: String) {
        val request = Request.Builder()
            .url(url)
            .post(message.toRequestBody(JSON))
            .build()

        // 这种不需要返回值的，parser 直接返回 Unit
        executeRequest(request) { /* 仅执行，不需解析内容 */ }
    }


    /**
     * 下载 APK 文件
     * @param url 下载地址* @param targetFile 存放的目标文件
     */
    suspend fun downloadApk(
        url: String,
        targetFile: java.io.File,
        onProgress: (Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false

                    val body = response.body ?: return@withContext false
                    val totalSize = body.contentLength()
                    var downloadedSize = 0L

                    body.byteStream().use { inputStream ->
                        targetFile.outputStream().use { outputStream ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                downloadedSize += bytesRead

                                // 2. 直接传原始字节，不要在这里做数学运算
                                onProgress(downloadedSize, totalSize)
                            }
                        }
                    }
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载失败: ${e.message}")
                false
            }
        }
}