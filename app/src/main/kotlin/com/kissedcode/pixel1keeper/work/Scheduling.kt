package com.kissedcode.pixel1keeper.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object Scheduling {
    val ZONE: ZoneId = ZoneId.of("Europe/Luxembourg")
    const val NIGHT = "night-cycle"
    const val SCHEDULER = "scheduler"
    const val CLEANUP = "cleanup"
    const val REPORT = "report"

    fun ensurePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(SCHEDULER, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SchedulerWorker>(15, TimeUnit.MINUTES).build())
        wm.enqueueUniquePeriodicWork(CLEANUP, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(30, TimeUnit.MINUTES).build())
    }

    fun startNight(context: Context, onlyCharging: Boolean, manual: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(onlyCharging)
            .build()
        val req = OneTimeWorkRequestBuilder<NightCycleWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(NightCycleWorker.KEY_MANUAL to manual))
            .apply { if (manual) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NIGHT, ExistingWorkPolicy.KEEP, req)
    }

    /** Отчёт раз в сутки в заданное время: one-time с задержкой, перепланируется после отправки. */
    fun scheduleReport(context: Context, hhmm: String, replace: Boolean) {
        val t = runCatching { LocalTime.parse(hhmm) }.getOrDefault(LocalTime.of(7, 30))
        val now = ZonedDateTime.now(ZONE)
        var next = now.with(t)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<ReportWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REPORT, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    /** Внутри ли ночного окна [from, to) с учётом перехода через полночь. */
    fun inWindow(hour: Int, from: Int, to: Int): Boolean =
        if (from == to) true else if (from < to) hour in from until to else hour >= from || hour < to

    fun nowHour(): Int = ZonedDateTime.now(ZONE).hour
}
