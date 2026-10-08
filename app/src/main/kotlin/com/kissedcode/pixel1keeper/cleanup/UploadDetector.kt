package com.kissedcode.pixel1keeper.cleanup

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.kissedcode.pixel1keeper.data.KeeperDb
import com.kissedcode.pixel1keeper.data.Status
import com.kissedcode.pixel1keeper.data.TrafficMark
import com.kissedcode.pixel1keeper.device.DeviceHealth

/**
 * Оценка «Google Photos загрузил» по исходящему Wi-Fi трафику его UID.
 * FIFO: файлы считаются загруженными в порядке появления, пока их сумма ≤ оценки отправленного.
 */
class UploadDetector(private val context: Context, private val db: KeeperDb) {
    private val nsm get() = context.getSystemService(NetworkStatsManager::class.java)

    private fun photosUid(): Int? = runCatching {
        context.packageManager.getApplicationInfo(DeviceHealth.GOOGLE_PHOTOS, 0).uid
    }.getOrNull()

    /** Wi-Fi tx Google Photos за интервал, байт. */
    fun txBytes(from: Long, to: Long): Long? {
        val uid = photosUid() ?: return null
        return runCatching {
            @Suppress("DEPRECATION")
            nsm.queryDetailsForUid(ConnectivityManager.TYPE_WIFI, null, from, to, uid).use { stats ->
                val b = NetworkStats.Bucket()
                var sum = 0L
                while (stats.hasNextBucket()) { stats.getNextBucket(b); sum += b.txBytes }
                sum
            }
        }.getOrNull()
    }

    data class Snapshot(val txLast30m: Long, val txLast24h: Long)

    /** Снимок трафика + пометка UPLOADED_ASSUMED. Возвращает null без доступа к статистике. */
    suspend fun evaluate(now: Long = System.currentTimeMillis()): Snapshot? {
        val traffic = db.trafficDao()
        val dao = db.itemDao()
        val first = traffic.first()
        val origin = first?.ts ?: (now - 60_000)
        val total = txBytes(origin, now) ?: return null
        traffic.insert(TrafficMark(now, total, dao.everOnDeviceBytes()))
        if (first == null) return Snapshot(0, 0)

        val m30 = traffic.atOrBefore(now - 30 * 60_000L)
        val m24 = traffic.atOrBefore(now - 24 * 3_600_000L) ?: first
        val last30 = total - (m30?.photosTxBytes ?: 0)
        val last24 = total - m24.photosTxBytes
        traffic.trim(now - 3 * 24 * 3_600_000L)

        // Оценка загруженного, начиная с файлов, появившихся после первой отметки.
        val estimate = (total * 0.97).toLong()
        if (last30 < 1_000_000L) {
            var covered = 0L
            for (it in dao.onDevice()) {
                if ((it.onDeviceAt ?: 0) < origin) continue
                covered += it.size ?: 0
                if (covered > estimate) break
                if (it.status == Status.ON_DEVICE) dao.update(it.copy(status = Status.UPLOADED_ASSUMED, statusChangedAt = now))
            }
        }
        return Snapshot(last30, last24)
    }
}
