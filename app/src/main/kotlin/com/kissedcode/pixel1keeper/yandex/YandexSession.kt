package com.kissedcode.pixel1keeper.yandex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.random.Random

/** Получает `sk` со страницы веб-клиента Диска. Ничего не хранит, кроме кэша в памяти. */
class YandexSession(private val http: OkHttpClient) {
    @Volatile private var cachedSk: String? = null
    @Volatile var connectionId: String = newConnectionId()
        private set

    fun reset() {
        cachedSk = null
        connectionId = newConnectionId()
    }

    suspend fun sk(): String = cachedSk ?: refresh()

    suspend fun refresh(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(PAGE)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ru,en;q=0.8")
            .build()
        http.newCall(req).execute().use { r ->
            val loc = r.header("Location").orEmpty()
            if (r.isRedirect) {
                if ("captcha" in loc || "showcaptcha" in loc) throw RateLimited("captcha on page")
                throw SessionExpired("redirect to passport")
            }
            if (r.code == 429) throw RateLimited("HTTP 429 on page")
            if (r.code == 401 || r.code == 403) throw SessionExpired("HTTP ${r.code}")
            val html = r.body?.string().orEmpty()
            val sk = SK_RE.find(html)?.groupValues?.get(1)
            if (sk.isNullOrEmpty()) throw SessionExpired("no sk in page")
            cachedSk = sk
            sk
        }
    }

    /** Проверка после входа: страница отдаёт sk и пользователь не аноним. */
    suspend fun validate(): Boolean = runCatching { refresh() }.isSuccess

    fun updateSk(sk: String) { cachedSk = sk }

    companion object {
        const val PAGE = "https://disk.yandex.ru/client/photo"
        val SK_RE = Regex("\"sk\":\"([^\"]+)\"")
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        private fun newConnectionId() = System.currentTimeMillis().toString() + Random.nextInt(100, 999)
    }
}
