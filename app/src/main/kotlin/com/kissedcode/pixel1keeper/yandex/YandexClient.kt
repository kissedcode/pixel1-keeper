package com.kissedcode.pixel1keeper.yandex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Вызовы внутреннего API веб-клиента Диска (`models-v2`). Только чтение:
 * любой метод вне allow-list — исключение до отправки запроса.
 */
class YandexClient(private val http: OkHttpClient, private val session: YandexSession) {
    private val mutex = Mutex()
    private var lastCallAt = 0L
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun call(method: String, params: JsonObject, minIntervalMs: Long = 1000): JsonObject {
        require(method in ALLOWED) { "method not allowed: $method" }
        return mutex.withLock {
            val wait = lastCallAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                callOnce(method, params, retrySk = true)
            } finally {
                lastCallAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun callOnce(method: String, params: JsonObject, retrySk: Boolean): JsonObject =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("sk", session.sk())
                put("connection_id", session.connectionId)
                put("apiMethod", method)
                put("requestParams", params)
            }.toString()
            val req = Request.Builder()
                .url("$BASE?m=$method")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", YandexSession.USER_AGENT)
                .header("Accept", "application/json")
                .header("Origin", "https://disk.yandex.ru")
                .header("Referer", YandexSession.PAGE)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val text = http.newCall(req).execute().use { r ->
                val loc = r.header("Location").orEmpty()
                when {
                    r.code == 429 -> throw RateLimited("HTTP 429")
                    r.isRedirect && "captcha" in loc -> throw RateLimited("captcha")
                    r.isRedirect -> throw SessionExpired("redirect")
                    r.code == 401 || r.code == 403 -> throw SessionExpired("HTTP ${r.code}")
                }
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw YandexApiError("$method HTTP ${r.code}")
                t
            }
            if (text.trimStart().startsWith("<")) {
                if ("captcha" in text.lowercase()) throw RateLimited("captcha html")
                throw SessionExpired("html instead of json")
            }
            val obj = json.parseToJsonElement(text).jsonObject
            // Ошибка sk: Яндекс присылает новый sk — обновляем и повторяем один раз.
            val newSk = (obj["newSk"] ?: obj["sk"])?.let { (it as? JsonPrimitive)?.content }
            val err = obj["error"]
            if (err != null) {
                if (retrySk && (newSk != null || err.toString().contains("sk", ignoreCase = true))) {
                    if (newSk != null) session.updateSk(newSk) else session.refresh()
                    return@withContext callOnce(method, params, retrySk = false)
                }
                if (err.toString().contains("unauthorized", true) || err.toString().contains("auth", true)) {
                    throw SessionExpired("api auth error")
                }
                throw YandexApiError("$method: ${err.toString().take(200)}")
            }
            obj
        }

    // --- типизированные обёртки

    suspend fun initSnapshot(): Pair<String, Long> {
        val r = call("intapi/photo-init-snapshot", buildJsonObject { })
        return r.str("photoslice_id")!! to r.long("revision")!!
    }

    suspend fun getDeltas(id: String, baseRevision: Long): JsonObject =
        call("intapi/photo-get-deltas", buildJsonObject {
            put("id", id); put("base_revision", baseRevision); put("locale", "ru")
        })

    suspend fun getSnapshot(id: String): JsonObject =
        call("intapi/photo-get-snapshot", buildJsonObject { put("id", id) })

    suspend fun clustersWithResources(sliceId: String, clusters: Map<String, Int>, hideScreenshots: Boolean): JsonObject =
        call("intapi/photo-get-clusters-with-resources", buildJsonObject {
            put("photosliceId", sliceId)
            put("clusters", buildJsonObject {
                clusters.forEach { (cid, size) ->
                    put(cid, buildJsonObject {
                        put("range", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(maxOf(size - 1, 0)))))
                    })
                }
            })
            put("hideScreenshots", hideScreenshots)
        }, minIntervalMs = 3000)

    data class FileInfo(val path: String, val name: String, val etimeSec: Long?, val size: Long, val mime: String?, val mediaType: String?)

    suspend fun info(path: String): FileInfo {
        val r = call("mpfs/info", buildJsonObject { put("path", path); put("meta", "") })
        val meta = r["meta"]?.jsonObject
        return FileInfo(
            path = r.str("path") ?: path,
            name = r.str("name") ?: path.substringAfterLast('/'),
            etimeSec = r.long("etime") ?: meta?.long("etime"),
            size = meta?.long("size") ?: throw YandexApiError("no size for file"),
            mime = meta.str("mimetype"),
            mediaType = meta.str("mediatype"),
        )
    }

    /** Подписанная ссылка на скачивание; не хранится, берётся перед каждой попыткой. */
    suspend fun downloadUrl(path: String): String {
        val r = call("mpfs/url", buildJsonObject { put("path", path) })
        val f = r.str("file") ?: throw YandexApiError("no download url")
        return if (f.startsWith("//")) "https:$f" else f
    }

    companion object {
        const val BASE = "https://disk.yandex.ru/models-v2"
        val ALLOWED = setOf(
            "intapi/photo-init-snapshot",
            "intapi/photo-get-deltas",
            "intapi/photo-get-snapshot",
            "intapi/photo-get-clusters-with-resources",
            "mpfs/info",
            "mpfs/url",
        )
    }
}

internal fun JsonObject?.str(k: String): String? = (this?.get(k) as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
internal fun JsonObject?.long(k: String): Long? = (this?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
internal fun JsonElement.objOrNull(): JsonObject? = this as? JsonObject
