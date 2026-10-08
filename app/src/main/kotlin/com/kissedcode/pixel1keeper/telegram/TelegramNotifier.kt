package com.kissedcode.pixel1keeper.telegram

import com.kissedcode.pixel1keeper.data.EventLog
import com.kissedcode.pixel1keeper.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class TelegramNotifier(
    private val http: OkHttpClient,
    private val settings: SettingsStore,
    private val log: EventLog,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun configured(): Boolean = settings.get().let { it.telegramToken.isNotBlank() && it.telegramChatId.isNotBlank() }

    /** Отправка с 3 попытками. Ошибки не пробрасываются. */
    suspend fun send(text: String, token: String? = null, chatId: String? = null): Result<Unit> {
        val s = settings.get()
        val t = token ?: s.telegramToken
        val c = chatId ?: s.telegramChatId
        if (t.isBlank() || c.isBlank()) {
            log.info("TELEGRAM", "not configured, message kept in log: ${text.take(200)}")
            return Result.failure(IllegalStateException("not configured"))
        }
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                withContext(Dispatchers.IO) {
                    val body = FormBody.Builder()
                        .add("chat_id", c).add("text", text).add("disable_web_page_preview", "true").build()
                    http.newCall(Request.Builder().url("https://api.telegram.org/bot$t/sendMessage").post(body).build())
                        .execute().use { r -> if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}") }
                }
                return Result.success(Unit)
            } catch (e: Exception) {
                last = e
                delay(2000L * (attempt + 1))
            }
        }
        log.warn("TELEGRAM", "send failed: ${last?.message}")
        return Result.failure(last ?: IllegalStateException("send failed"))
    }

    /** Тревога не чаще раза в 6 часов на тип. */
    suspend fun alert(type: String, text: String) {
        if (settings.shouldAlert(type, System.currentTimeMillis(), 6 * 3_600_000L)) {
            log.warn("ALERT", "$type: $text")
            send(text)
        }
    }

    /** chat.id последнего личного сообщения боту. */
    suspend fun findChatId(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("https://api.telegram.org/bot$token/getUpdates").build()).execute().use { r ->
                val o = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                o["result"]?.jsonArray?.reversed()?.firstNotNullOfOrNull { u ->
                    val msg = (u.jsonObject["message"] ?: u.jsonObject["my_chat_member"]) as? JsonObject
                    val chat = msg?.get("chat") as? JsonObject
                    if ((chat?.get("type") as? JsonPrimitive)?.content == "private") (chat["id"] as? JsonPrimitive)?.content else null
                }
            }
        }.getOrNull()
    }
}
