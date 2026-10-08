package com.kissedcode.pixel1keeper.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kissedcode.pixel1keeper.container

/** Раз в 15 минут: если окно открыто и нет паузы — ставит ночной цикл; держит расписание отчёта. */
class SchedulerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.get()
        Scheduling.scheduleReport(applicationContext, s.reportTime, replace = false)
        if (s.paused) return Result.success()
        if (c.settings.pausedUntil() > System.currentTimeMillis()) return Result.success()
        if (!Scheduling.inWindow(Scheduling.nowHour(), s.windowFromHour, s.windowToHour)) return Result.success()
        Scheduling.startNight(applicationContext, s.onlyCharging, manual = false)
        return Result.success()
    }
}
