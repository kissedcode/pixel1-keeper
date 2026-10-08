package com.kissedcode.pixel1keeper.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.device.DeviceHealth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        try {
            c.telegram.send(buildReport(applicationContext))
        } finally {
            Scheduling.scheduleReport(applicationContext, c.settings.get().reportTime, replace = true)
        }
        return Result.success()
    }

    companion object {
        suspend fun buildReport(ctx: Context): String {
            val c = ctx.container
            val since = System.currentTimeMillis() - 24 * 3_600_000L
            val dao = c.db.itemDao()
            val dl = dao.downloadedSince(since)
            val del = dao.deletedSince(since)
            val pending = dao.onDeviceCounts()
            val failed = dao.failedCounts()
            val free = DeviceHealth.gb(c.health.freeBytes())
            if (dl.n == 0 && pending.n == 0 && failed.n == 0) return ctx.getString(R.string.tg_report_quiet, free)
            val date = ZonedDateTime.now(Scheduling.ZONE).format(DateTimeFormatter.ofPattern("dd.MM"))
            return buildString {
                appendLine(ctx.getString(R.string.tg_report_header, date))
                appendLine(ctx.getString(R.string.tg_report_new, dl.n, DeviceHealth.gb(dl.bytes ?: 0), del.n))
                appendLine(ctx.getString(R.string.tg_report_pending, pending.n, DeviceHealth.gb(pending.bytes ?: 0)))
                if (failed.n > 0) appendLine(ctx.getString(R.string.tg_report_failed, failed.n))
                append(ctx.getString(R.string.tg_report_free, free, c.health.battery().tempC))
            }
        }
    }
}
