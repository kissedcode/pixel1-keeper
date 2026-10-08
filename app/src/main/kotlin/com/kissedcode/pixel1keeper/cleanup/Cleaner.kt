package com.kissedcode.pixel1keeper.cleanup

import android.net.Uri
import com.kissedcode.pixel1keeper.data.CleanupMode
import com.kissedcode.pixel1keeper.data.EventLog
import com.kissedcode.pixel1keeper.data.KeeperDb
import com.kissedcode.pixel1keeper.data.SettingsStore
import com.kissedcode.pixel1keeper.data.Status
import com.kissedcode.pixel1keeper.download.MediaStoreWriter

/** Удаление файлов, которые Google Photos уже загрузил (оценочно) или которые старше таймера. */
class Cleaner(
    private val db: KeeperDb,
    private val detector: UploadDetector,
    private val media: MediaStoreWriter,
    private val settings: SettingsStore,
    private val log: EventLog,
) {
    data class Outcome(val deleted: Int, val stuckFiles: Int, val stuckBytes: Long, val photosSilent24h: Boolean)

    suspend fun run(usageAccess: Boolean, now: Long = System.currentTimeMillis()): Outcome {
        val s = settings.get()
        val mode = if (usageAccess) s.cleanupMode else CleanupMode.TIMER
        val snap = if (usageAccess) detector.evaluate(now) else null
        val dao = db.itemDao()
        var deleted = 0
        var stuck = 0
        var stuckBytes = 0L
        for (it in dao.onDevice()) {
            val age = now - (it.onDeviceAt ?: now)
            val remove = when (mode) {
                CleanupMode.CAREFUL -> it.status == Status.UPLOADED_ASSUMED && age >= 6 * HOUR
                CleanupMode.TIMER -> age >= s.deleteAfterHours * HOUR
            }
            if (remove) {
                val ok = it.mediaUri?.let { u -> media.delete(Uri.parse(u)) } ?: true
                if (ok) {
                    dao.update(it.copy(status = Status.DELETED, statusChangedAt = now, mediaUri = null))
                    deleted++
                }
            } else if (it.status == Status.ON_DEVICE && age >= 24 * HOUR) {
                stuck++
                stuckBytes += it.size ?: 0
            }
        }
        if (deleted > 0) log.info("CLEANUP", "deleted $deleted (${mode.name})")
        val silent = snap != null && stuck > 0 && snap.txLast24h < 50L * 1024 * 1024
        return Outcome(deleted, stuck, stuckBytes, silent)
    }

    companion object { const val HOUR = 3_600_000L }
}
