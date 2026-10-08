package com.kissedcode.pixel1keeper.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kissedcode.pixel1keeper.KeeperApp
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.data.Status
import com.kissedcode.pixel1keeper.device.DeviceHealth
import com.kissedcode.pixel1keeper.download.Downloader
import com.kissedcode.pixel1keeper.yandex.RateLimited
import com.kissedcode.pixel1keeper.yandex.SessionExpired
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Ночной цикл: синхронизация списка → скачивание в рамках бюджета места. */
class NightCycleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val c = ctx.container
    @Volatile private var hot = false

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0)

    private fun foreground(done: Int, total: Int): ForegroundInfo {
        val n: Notification = NotificationCompat.Builder(applicationContext, KeeperApp.CHANNEL_WORK)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.notif_title))
            .setContentText(applicationContext.getString(R.string.notif_text, done, total, DeviceHealth.gb(c.health.freeBytes())))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val s = c.settings.get()
        val ctx = applicationContext
        if (s.paused && !manual) return Result.success()
        val now = System.currentTimeMillis()
        if (c.settings.pausedUntil() > now) return Result.success()
        runCatching { setForeground(foreground(0, 0)) }

        c.log.info("CYCLE", if (manual) "manual start" else "night start")
        c.session.reset()
        var downloaded = 0
        try {
            c.sync.sync().also { if (it > 0) c.log.info("SYNC", "new in queue: $it") }
            c.settings.setSessionExpired(false)

            // Незавершённые pending-записи старше суток — удалить и скачать заново.
            c.db.itemDao().downloading().filter { now - it.statusChangedAt > 24 * 3_600_000L }.forEach {
                it.mediaUri?.let { u -> c.mediaStore.delete(android.net.Uri.parse(u)) }
                c.db.itemDao().update(it.copy(status = Status.QUEUED, mediaUri = null, downloadedBytes = 0, statusChangedAt = now))
            }

            // FAILED — повтор раз в сутки
            c.db.itemDao().failedBefore(now - 24 * 3_600_000L).forEach {
                c.db.itemDao().update(it.copy(status = Status.QUEUED, statusChangedAt = now))
            }

            val reserve = s.reserveGb * 1_073_741_824L
            val queue = c.db.itemDao().downloadQueue()
            val budgetLock = Mutex()
            var budget = c.health.freeBytes() - reserve - c.db.itemDao().bytesInFlight()
            if (budget <= 0) {
                lowSpaceAlert(reserve)
            } else if (queue.isNotEmpty()) {
                setForeground(foreground(0, queue.size))
                val sem = Semaphore(2)
                var stop = false
                coroutineScope {
                    queue.map { item ->
                        async {
                            sem.withPermit {
                                if (stop || !canContinue(s, manual)) { stop = true; return@withPermit }
                                val b = budgetLock.withLock { budget }
                                if (b <= 0) { stop = true; return@withPermit }
                                when (val r = c.downloader.download(item, b)) {
                                    is Downloader.Result.Done -> budgetLock.withLock {
                                        budget -= r.bytes
                                        downloaded++
                                        runCatching { setForeground(foreground(downloaded, queue.size)) }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }.awaitAll()
                }
                if (c.health.freeBytes() < reserve / 2) lowSpaceAlert(reserve)
            }
            c.settings.onCycleOk()
        } catch (e: SessionExpired) {
            c.log.warn("SESSION", e.message ?: "expired")
            c.settings.setSessionExpired(true)
            c.telegram.alert("session", ctx.getString(R.string.tg_alert_session))
        } catch (e: RateLimited) {
            val until = c.settings.onRateLimited(System.currentTimeMillis())
            val at = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(Scheduling.ZONE).format(Instant.ofEpochMilli(until))
            c.log.warn("RATE", "${e.message}; paused until $at")
            c.telegram.alert("rate", ctx.getString(R.string.tg_alert_rate, at))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            c.log.error("CYCLE", "${e.javaClass.simpleName}: ${e.message}")
            c.telegram.alert("error", ctx.getString(R.string.tg_alert_error, e.javaClass.simpleName))
        } finally {
            val deleted = runCatching { c.cleaner.run(c.health.hasUsageAccess()).deleted }.getOrDefault(0)
            c.settings.setLastRun(System.currentTimeMillis(), downloaded, deleted)
            c.log.info("CYCLE", "done: downloaded=$downloaded, deleted=$deleted")
        }
        return Result.success()
    }

    private suspend fun canContinue(s: com.kissedcode.pixel1keeper.data.Settings, manual: Boolean): Boolean {
        if (!c.health.onUnmeteredWifi()) return false
        val bat = c.health.battery()
        if (s.onlyCharging && !bat.plugged) return false
        if (bat.tempC >= DeviceHealth.HOT_C) {
            c.telegram.alert("hot", applicationContext.getString(R.string.tg_alert_hot, bat.tempC))
            hot = true
        }
        if (hot) {
            // Ждём остывания до 38°C, проверяя раз в минуту, но не дольше окна.
            while (c.health.battery().tempC > DeviceHealth.COOL_C) {
                if (!manual && !Scheduling.inWindow(Scheduling.nowHour(), s.windowFromHour, s.windowToHour)) return false
                kotlinx.coroutines.delay(60_000)
            }
            hot = false
        }
        if (!manual && !Scheduling.inWindow(Scheduling.nowHour(), s.windowFromHour, s.windowToHour)) return false
        if (!manual && c.settings.get().paused) return false
        return true
    }

    private suspend fun lowSpaceAlert(reserve: Long) {
        val free = c.health.freeBytes()
        if (free < reserve) c.telegram.alert("space", applicationContext.getString(R.string.tg_alert_space, DeviceHealth.gb(free)))
    }

    companion object {
        const val KEY_MANUAL = "manual"
        const val NOTIF_ID = 42
    }
}
