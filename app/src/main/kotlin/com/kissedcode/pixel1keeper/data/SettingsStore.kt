package com.kissedcode.pixel1keeper.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kissedcode.pixel1keeper.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("keeper")

enum class CleanupMode { CAREFUL, TIMER }

data class Settings(
    val windowFromHour: Int = 0,
    val windowToHour: Int = 7,
    val onlyCharging: Boolean = true,
    val reserveGb: Int = 2,
    val skipScreenshots: Boolean = true,
    val cleanupMode: CleanupMode = CleanupMode.CAREFUL,
    val deleteAfterHours: Int = 48,
    val telegramToken: String = "",
    val telegramChatId: String = "",
    val reportTime: String = "07:30",
    val paused: Boolean = false,
    val setupGphotosDone: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private val ds get() = context.dataStore
    private val crypto = SecretBox()

    private object K {
        val from = intPreferencesKey("windowFrom")
        val to = intPreferencesKey("windowTo")
        val charging = booleanPreferencesKey("onlyCharging")
        val reserve = intPreferencesKey("reserveGb")
        val skipScreens = booleanPreferencesKey("skipScreenshots")
        val cleanup = stringPreferencesKey("cleanupMode")
        val deleteAfter = intPreferencesKey("deleteAfterHours")
        val tgToken = stringPreferencesKey("tgTokenEnc")
        val tgChat = stringPreferencesKey("tgChat")
        val report = stringPreferencesKey("reportTime")
        val paused = booleanPreferencesKey("paused")
        val gphotos = booleanPreferencesKey("setupGphotos")
        val sliceId = stringPreferencesKey("photosliceId")
        val revision = longPreferencesKey("revision")
        val backfillDone = booleanPreferencesKey("backfillDone")
        val pausedUntil = longPreferencesKey("pausedUntil")
        val backoff = intPreferencesKey("rateLimitBackoffLevel")
        val lastRun = longPreferencesKey("lastRunAt")
        val lastRunDl = intPreferencesKey("lastRunDownloaded")
        val lastRunDel = intPreferencesKey("lastRunDeleted")
        val sessionExpired = booleanPreferencesKey("sessionExpired")
        fun alert(type: String) = longPreferencesKey("lastAlertAt.$type")
    }

    val flow: Flow<Settings> = ds.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        windowFromHour = this[K.from] ?: 0,
        windowToHour = this[K.to] ?: 7,
        onlyCharging = this[K.charging] ?: true,
        reserveGb = this[K.reserve] ?: 2,
        skipScreenshots = this[K.skipScreens] ?: true,
        cleanupMode = this[K.cleanup]?.let { runCatching { CleanupMode.valueOf(it) }.getOrNull() } ?: CleanupMode.CAREFUL,
        deleteAfterHours = this[K.deleteAfter] ?: 48,
        // Токен/chat id по умолчанию могут прийти из сборки (GitHub Secrets) — UI их перекрывает.
        telegramToken = this[K.tgToken]?.let { crypto.decrypt(it) } ?: BuildConfig.TG_TOKEN,
        telegramChatId = this[K.tgChat] ?: BuildConfig.TG_CHAT_ID,
        reportTime = this[K.report] ?: "07:30",
        paused = this[K.paused] ?: false,
        setupGphotosDone = this[K.gphotos] ?: false,
    )

    suspend fun get(): Settings = flow.first()

    suspend fun save(s: Settings) = ds.edit {
        it[K.from] = s.windowFromHour.coerceIn(0, 23)
        it[K.to] = s.windowToHour.coerceIn(0, 23)
        it[K.charging] = s.onlyCharging
        it[K.reserve] = s.reserveGb.coerceIn(1, 20)
        it[K.skipScreens] = s.skipScreenshots
        it[K.cleanup] = s.cleanupMode.name
        it[K.deleteAfter] = s.deleteAfterHours.coerceIn(1, 24 * 14)
        it[K.tgToken] = crypto.encrypt(s.telegramToken.trim())
        it[K.tgChat] = s.telegramChatId.trim()
        it[K.report] = s.reportTime.trim()
    }

    suspend fun setPaused(v: Boolean) = ds.edit { it[K.paused] = v }
    suspend fun setGphotosDone(v: Boolean) = ds.edit { it[K.gphotos] = v }

    // --- курсоры Photoslice
    suspend fun slice(): Pair<String, Long>? {
        val p = ds.data.first()
        val id = p[K.sliceId] ?: return null
        return id to (p[K.revision] ?: return null)
    }
    suspend fun setSlice(id: String, revision: Long) = ds.edit { it[K.sliceId] = id; it[K.revision] = revision }
    suspend fun setRevision(revision: Long) = ds.edit { it[K.revision] = revision }
    suspend fun clearSlice() = ds.edit { it.remove(K.sliceId); it.remove(K.revision) }
    suspend fun backfillDone(): Boolean = ds.data.first()[K.backfillDone] ?: false
    suspend fun setBackfillDone() = ds.edit { it[K.backfillDone] = true }

    // --- паузы/бэкофф
    val pausedUntilFlow: Flow<Long> = ds.data.map { it[K.pausedUntil] ?: 0L }
    suspend fun pausedUntil(): Long = ds.data.first()[K.pausedUntil] ?: 0L
    suspend fun onRateLimited(now: Long): Long {
        var until = 0L
        ds.edit {
            val level = it[K.backoff] ?: 0
            val hours = minOf(24L shl level, 96L)
            until = now + hours * 3_600_000L
            it[K.pausedUntil] = until
            it[K.backoff] = level + 1
        }
        return until
    }
    suspend fun onCycleOk() = ds.edit { it[K.backoff] = 0 }

    val sessionExpiredFlow: Flow<Boolean> = ds.data.map { it[K.sessionExpired] ?: false }
    suspend fun setSessionExpired(v: Boolean) = ds.edit { it[K.sessionExpired] = v }

    // --- последний запуск
    data class LastRun(val at: Long, val downloaded: Int, val deleted: Int)
    val lastRunFlow: Flow<LastRun?> = ds.data.map { p ->
        p[K.lastRun]?.let { LastRun(it, p[K.lastRunDl] ?: 0, p[K.lastRunDel] ?: 0) }
    }
    suspend fun setLastRun(at: Long, downloaded: Int, deleted: Int) = ds.edit {
        it[K.lastRun] = at; it[K.lastRunDl] = downloaded; it[K.lastRunDel] = deleted
    }

    // --- дедуп тревог
    suspend fun shouldAlert(type: String, now: Long, minIntervalMs: Long): Boolean {
        var ok = false
        ds.edit {
            val last = it[K.alert(type)] ?: 0L
            if (now - last >= minIntervalMs) { it[K.alert(type)] = now; ok = true }
        }
        return ok
    }
}
