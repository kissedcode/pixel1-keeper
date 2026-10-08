package com.kissedcode.pixel1keeper

import com.kissedcode.pixel1keeper.yandex.YandexClient
import com.kissedcode.pixel1keeper.yandex.YandexSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowListTest {
    private val http = OkHttpClient()
    private val client = YandexClient(http, YandexSession(http))

    @Test fun onlyReadMethodsAllowed() {
        assertTrue("mpfs/url" in YandexClient.ALLOWED)
        listOf("mpfs/bulk-move", "mpfs/delete", "mpfs/trash-append", "mpfs/copy").forEach { assertFalse(it in YandexClient.ALLOWED) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun mutatingMethodThrowsBeforeNetwork() {
        runBlocking { client.call("mpfs/bulk-delete", buildJsonObject { }) }
    }
}
