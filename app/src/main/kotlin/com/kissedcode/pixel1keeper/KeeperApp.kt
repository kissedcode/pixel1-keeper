package com.kissedcode.pixel1keeper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.kissedcode.pixel1keeper.work.Scheduling

class KeeperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WORK, getString(R.string.notif_channel_work), NotificationManager.IMPORTANCE_LOW)
        )
        Scheduling.ensurePeriodic(this)
    }

    companion object {
        const val CHANNEL_WORK = "work"
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as KeeperApp).container
