package com.kissedcode.pixel1keeper.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.OutputStream

/** Файлы в `DCIM/Yandex/` через MediaStore: pending-вставка, дозапись, публикация, удаление. */
class MediaStoreWriter(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun isVideo(mime: String?, name: String): Boolean =
        mime?.startsWith("video/") == true || name.substringAfterLast('.').lowercase() in setOf("mp4", "mov", "3gp", "mkv", "webm")

    private fun collection(video: Boolean): Uri =
        if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Создаёт скрытую (IS_PENDING=1) запись; при занятом имени добавляет суффикс « (2)», « (3)»… */
    fun beginPending(name: String, mime: String?, etimeSec: Long): Uri {
        val video = isVideo(mime, name)
        val unique = uniqueName(name, video)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, unique)
            mime?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.DATE_TAKEN, etimeSec * 1000)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection(video), values) ?: error("MediaStore insert failed")
    }

    private fun uniqueName(name: String, video: Boolean): String {
        var candidate = name
        var n = 2
        while (exists(candidate, video)) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            n++
        }
        return candidate
    }

    private fun exists(name: String, video: Boolean): Boolean {
        resolver.query(
            collection(video), arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(RELATIVE_PATH, name), null,
        )?.use { return it.moveToFirst() }
        return false
    }

    /** Текущий размер записанного файла (для докачки). null — записи больше нет. */
    fun writtenBytes(uri: Uri): Long? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull()

    fun openAppend(uri: Uri): OutputStream = resolver.openOutputStream(uri, "wa") ?: error("cannot open $uri")
    fun openTruncate(uri: Uri): OutputStream = resolver.openOutputStream(uri, "wt") ?: error("cannot open $uri")

    fun publish(uri: Uri) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    fun delete(uri: Uri): Boolean = runCatching { resolver.delete(uri, null, null) >= 0 }.getOrDefault(false)

    companion object {
        const val RELATIVE_PATH = "DCIM/Yandex/"
    }
}
