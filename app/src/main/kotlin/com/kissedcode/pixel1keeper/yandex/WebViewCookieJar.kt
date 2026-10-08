package com.kissedcode.pixel1keeper.yandex

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Общее хранилище cookies с WebView входа: OkHttp читает и продлевает ту же сессию. */
class WebViewCookieJar : CookieJar {
    private val cm: CookieManager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val u = url.toString()
        cookies.forEach { cm.setCookie(u, it.toString()) }
        cm.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cm.getCookie(url.toString()) ?: return emptyList()
        return header.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    }
}
