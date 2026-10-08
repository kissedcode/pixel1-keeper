package com.kissedcode.pixel1keeper.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.device.DeviceHealth

class CleanupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val out = runCatching { c.cleaner.run(c.health.hasUsageAccess()) }.getOrElse {
            c.log.error("CLEANUP", it.message ?: it.javaClass.simpleName)
            return Result.success()
        }
        if (out.photosSilent24h) {
            c.telegram.alert("gphotos", applicationContext.getString(R.string.tg_alert_gphotos, out.stuckFiles, DeviceHealth.gb(out.stuckBytes)))
        }
        return Result.success()
    }
}
