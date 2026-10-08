package com.kissedcode.pixel1keeper.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.yandex.YandexSession
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking = false
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.login_hint), Modifier.padding(16.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        // Только домены Яндекса; всё остальное не открываем.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val host = request.url.host.orEmpty()
                            return !ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (checking || !url.startsWith("https://disk.yandex.ru/client/")) return
                            checking = true
                            CookieManager.getInstance().flush()
                            scope.launch {
                                val app = ctx.container
                                app.session.reset()
                                if (app.session.validate()) {
                                    app.settings.setSessionExpired(false)
                                    app.log.info("LOGIN", "ok")
                                    Toast.makeText(ctx, R.string.login_ok, Toast.LENGTH_SHORT).show()
                                    onDone()
                                } else {
                                    checking = false
                                    Toast.makeText(ctx, R.string.login_fail, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    loadUrl(YandexSession.PAGE)
                }
            },
        )
    }
}

private val ALLOWED_HOSTS = listOf("yandex.ru", "yandex.com", "ya.ru", "yandex.net")
