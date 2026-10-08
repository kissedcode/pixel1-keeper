package com.kissedcode.pixel1keeper.data

import android.util.Log

/** Журнал событий: Room (видно в UI) + logcat. Никогда не пишем cookies, sk, токены и ссылки. */
class EventLog(private val dao: EventDao) {
    suspend fun info(type: String, message: String) = write("I", type, message)
    suspend fun warn(type: String, message: String) = write("W", type, message)
    suspend fun error(type: String, message: String) = write("E", type, message)

    private suspend fun write(level: String, type: String, message: String) {
        val safe = redact(message)
        Log.println(if (level == "E") Log.ERROR else if (level == "W") Log.WARN else Log.INFO, "Keeper", "$type: $safe")
        dao.insert(EventEntity(ts = System.currentTimeMillis(), level = level, type = type, message = safe))
        dao.trim()
    }

    companion object {
        private val urlRe = Regex("https?://\\S+|//downloader\\S+")
        private val botRe = Regex("bot\\d+:[A-Za-z0-9_-]+")
        fun redact(s: String): String = s.replace(urlRe, "<url>").replace(botRe, "bot<token>").take(500)
    }
}
