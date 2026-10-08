package com.kissedcode.pixel1keeper

import android.content.Context
import com.kissedcode.pixel1keeper.cleanup.Cleaner
import com.kissedcode.pixel1keeper.cleanup.UploadDetector
import com.kissedcode.pixel1keeper.data.EventLog
import com.kissedcode.pixel1keeper.data.KeeperDb
import com.kissedcode.pixel1keeper.data.SettingsStore
import com.kissedcode.pixel1keeper.device.DeviceHealth
import com.kissedcode.pixel1keeper.download.Downloader
import com.kissedcode.pixel1keeper.download.MediaStoreWriter
import com.kissedcode.pixel1keeper.telegram.TelegramNotifier
import com.kissedcode.pixel1keeper.yandex.PhotosliceSync
import com.kissedcode.pixel1keeper.yandex.WebViewCookieJar
import com.kissedcode.pixel1keeper.yandex.YandexClient
import com.kissedcode.pixel1keeper.yandex.YandexSession
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ручной DI: все синглтоны приложения. */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db: KeeperDb = KeeperDb.create(app)
    val settings = SettingsStore(app)
    val log = EventLog(db.eventDao())
    val health = DeviceHealth(app)

    /** Клиент для Яндекса: cookies из общего CookieManager (как у WebView). */
    val yandexHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .followRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Клиент для downloader и Telegram: без cookies. */
    val plainHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val session = YandexSession(yandexHttp)
    val yandex = YandexClient(yandexHttp, session)
    val sync = PhotosliceSync(yandex, db, settings, log)
    val mediaStore = MediaStoreWriter(app)
    val downloader = Downloader(plainHttp, yandex, mediaStore, db.itemDao(), log)
    val uploadDetector = UploadDetector(app, db)
    val cleaner = Cleaner(db, uploadDetector, mediaStore, settings, log)
    val telegram = TelegramNotifier(plainHttp, settings, log)
}
