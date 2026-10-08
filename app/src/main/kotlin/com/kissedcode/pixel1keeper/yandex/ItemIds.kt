package com.kissedcode.pixel1keeper.yandex

/** Разбор идентификаторов Photoslice. Чистые функции — покрыты unit-тестами. */
object ItemIds {
    /** `2_<etimeMs 19 цифр>_<hash>_<ver>` → etimeMs; null, если формат другой. */
    fun etimeMs(itemId: String): Long? {
        val parts = itemId.split('_')
        if (parts.size < 3) return null
        return parts[1].toLongOrNull()
    }

    /** Кластер `<fromMs>_<toMs>` → toMs. */
    fun clusterToMs(clusterId: String): Long? = clusterId.split('_').getOrNull(1)?.toLongOrNull()

    /** Путь из дельт (`photounlim:/X`) → форма для mpfs (`/photounlim/X`). */
    fun normalizePath(path: String): String = when {
        path.startsWith("photounlim:/") -> "/photounlim/" + path.removePrefix("photounlim:/")
        else -> path
    }

    fun fileName(path: String): String = path.substringAfterLast('/')
}
