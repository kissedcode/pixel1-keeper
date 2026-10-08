package com.kissedcode.pixel1keeper.download

import android.net.Uri
import com.kissedcode.pixel1keeper.data.EventLog
import com.kissedcode.pixel1keeper.data.ItemDao
import com.kissedcode.pixel1keeper.data.ItemEntity
import com.kissedcode.pixel1keeper.data.Status
import com.kissedcode.pixel1keeper.yandex.RateLimited
import com.kissedcode.pixel1keeper.yandex.SessionExpired
import com.kissedcode.pixel1keeper.yandex.YandexClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Скачивание одного файла в MediaStore с докачкой и проверкой размера. */
class Downloader(
    private val http: OkHttpClient,
    private val api: YandexClient,
    private val media: MediaStoreWriter,
    private val items: ItemDao,
    private val log: EventLog,
) {
    sealed interface Result {
        data class Done(val bytes: Long) : Result
        data object TooBig : Result
        data object Failed : Result
    }

    /** Заполняет size/mime из mpfs/info, если их ещё нет. */
    suspend fun resolveInfo(item: ItemEntity): ItemEntity {
        if (item.size != null) return item
        val info = api.info(item.path)
        val upd = item.copy(
            size = info.size, mime = info.mime,
            etime = info.etimeSec ?: item.etime,
            name = info.name.ifBlank { item.name },
        )
        items.update(upd)
        return upd
    }

    suspend fun download(start: ItemEntity, budget: Long): Result {
        var item = try { resolveInfo(start) } catch (e: SessionExpired) { throw e } catch (e: RateLimited) { throw e } catch (e: Exception) {
            return fail(start, "info: ${e.message}")
        }
        val size = item.size ?: return fail(item, "no size")
        if (size - item.downloadedBytes > budget) return Result.TooBig

        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            try {
                item = downloadOnce(item, size)
                return Result.Done(size)
            } catch (e: SessionExpired) { throw e } catch (e: RateLimited) { throw e } catch (e: IOException) {
                attempt++
                if (attempt >= 5) return fail(item, "network: ${e.message}")
                item = items.get(item.itemId) ?: item
                delay(2000L shl attempt)
            } catch (e: SizeMismatch) {
                return fail(item, e.message ?: "size mismatch", dropFile = true)
            }
        }
    }

    private class SizeMismatch(msg: String) : Exception(msg)

    private suspend fun downloadOnce(start: ItemEntity, size: Long): ItemEntity {
        var item = start
        // pending-запись: новая или существующая для докачки
        val prevUri = item.mediaUri?.let(Uri::parse)
        val prevBytes = prevUri?.let { media.writtenBytes(it) }
        val uri: Uri
        var have: Long
        if (prevUri == null || prevBytes == null) {
            uri = withContext(Dispatchers.IO) { media.beginPending(item.name, item.mime, item.etime) }
            have = 0L
        } else {
            uri = prevUri
            have = prevBytes
        }
        item = item.copy(status = Status.DOWNLOADING, mediaUri = uri.toString(), downloadedBytes = have, statusChangedAt = now())
        items.update(item)

        val url = api.downloadUrl(item.path) // ссылку не храним
        val reqB = Request.Builder().url(url)
        if (have > 0) reqB.header("Range", "bytes=$have-")
        withContext(Dispatchers.IO) {
            http.newCall(reqB.build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val append = have > 0 && r.code == 206
                if (have > 0 && !append) have = 0 // сервер проигнорировал Range — пишем заново
                val body = r.body ?: throw IOException("empty body")
                (if (append) media.openAppend(uri) else media.openTruncate(uri)).use { out ->
                    val buf = ByteArray(64 * 1024)
                    val input = body.byteStream()
                    var written = have
                    var sinceSave = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        sinceSave += n
                        if (sinceSave >= 4L * 1024 * 1024) {
                            out.flush()
                            items.update(item.copy(downloadedBytes = written))
                            sinceSave = 0
                        }
                    }
                    out.flush()
                    have = written
                }
            }
        }
        val actual = media.writtenBytes(uri) ?: have
        if (actual != size) throw SizeMismatch("size $actual != $size")
        withContext(Dispatchers.IO) { media.publish(uri) }
        val done = item.copy(status = Status.ON_DEVICE, downloadedBytes = size, onDeviceAt = now(), statusChangedAt = now(), lastError = null)
        items.update(done)
        return done
    }

    private suspend fun fail(item: ItemEntity, reason: String, dropFile: Boolean = false): Result {
        if (dropFile) item.mediaUri?.let { media.delete(Uri.parse(it)) }
        items.update(
            item.copy(
                status = Status.FAILED, attempts = item.attempts + 1, lastError = reason.take(200),
                statusChangedAt = now(), mediaUri = if (dropFile) null else item.mediaUri,
                downloadedBytes = if (dropFile) 0 else item.downloadedBytes,
            )
        )
        log.warn("DOWNLOAD", "${item.name}: $reason")
        return Result.Failed
    }

    private fun now() = System.currentTimeMillis()
}
