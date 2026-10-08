package com.kissedcode.pixel1keeper.yandex

import androidx.room.withTransaction
import com.kissedcode.pixel1keeper.BuildConfig
import com.kissedcode.pixel1keeper.data.EventLog
import com.kissedcode.pixel1keeper.data.ItemEntity
import com.kissedcode.pixel1keeper.data.KeeperDb
import com.kissedcode.pixel1keeper.data.SettingsStore
import com.kissedcode.pixel1keeper.data.Source
import com.kissedcode.pixel1keeper.data.Status
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Курсор ревизии Photoslice → очередь `items`. Только фото, снятые не раньше CUTOFF_MS. */
class PhotosliceSync(
    private val api: YandexClient,
    private val db: KeeperDb,
    private val settings: SettingsStore,
    private val log: EventLog,
    private val cutoffMs: Long = BuildConfig.CUTOFF_MS,
) {
    private val items get() = db.itemDao()

    /** Возвращает число новых элементов в очереди. */
    suspend fun sync(): Int {
        val skip = settings.get().skipScreenshots
        var slice = settings.slice()
        var added = 0
        if (slice == null || !settings.backfillDone()) {
            val (id, rev) = api.initSnapshot()
            settings.setSlice(id, rev)
            added += backfill(id, skip)
            settings.setBackfillDone()
            slice = id to rev
        }
        added += syncDeltas(slice.first, slice.second, skip)
        return added
    }

    private suspend fun syncDeltas(id: String, startRevision: Long, skip: Boolean): Int {
        var base = startRevision
        var added = 0
        repeat(50) { // защита от бесконечного цикла
            val r = try {
                api.getDeltas(id, base)
            } catch (e: YandexApiError) {
                // «ревизия слишком старая» или снимок протух — переинициализируем и догружаем с точки отсчёта.
                log.warn("SYNC", "deltas failed, reinit snapshot: ${e.message}")
                val (nid, nrev) = api.initSnapshot()
                settings.setSlice(nid, nrev)
                return added + backfill(nid, skip)
            }
            val target = r.long("revision") ?: return added
            val changes = r["items"]?.jsonArray.orEmpty()
            var last = base
            db.withTransaction {
                for (ch in changes) {
                    val o = ch.jsonObject
                    for (ic in o["items_changes"]?.jsonArray.orEmpty()) added += applyChange(ic.jsonObject, skip)
                    last = maxOf(last, o.long("revision") ?: last)
                }
                if (changes.isEmpty()) last = target
            }
            settings.setRevision(last)
            if (last >= target || last == base) {
                if (last < target) settings.setRevision(target)
                return added
            }
            base = last
        }
        return added
    }

    private suspend fun applyChange(c: JsonObject, skip: Boolean): Int {
        val type = c.str("change_type") ?: return 0
        val itemId = c.str("item_id") ?: return 0
        val data = c["data"] as? JsonObject
        val existing = items.get(itemId)
        if (type == "delete") {
            if (existing != null && existing.status == Status.QUEUED) {
                items.update(existing.copy(status = Status.SKIPPED, statusChangedAt = now(), lastError = "deleted in Yandex"))
            }
            return 0
        }
        val path = data.str("path")?.let(ItemIds::normalizePath)
        if (existing != null) {
            if (path != null && existing.status == Status.QUEUED && existing.path != path) {
                items.update(existing.copy(path = path, name = ItemIds.fileName(path)))
            }
            return 0
        }
        // insert или update неизвестного нам элемента — это новый кандидат.
        if (path == null) return 0
        val albums = (data?.get("albums") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        return enqueue(itemId, path, albums, Source.DELTA, skip)
    }

    /** Первичная догрузка: только кластеры, закончившиеся не раньше точки отсчёта. */
    private suspend fun backfill(id: String, skip: Boolean): Int {
        val snap = api.getSnapshot(id)
        val clusters = snap["items"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            val cid = o.str("id") ?: return@mapNotNull null
            val to = o.long("to") ?: ItemIds.clusterToMs(cid) ?: return@mapNotNull null
            if (to >= cutoffMs) cid to (o.long("size")?.toInt() ?: 0) else null
        }
        var added = 0
        for (chunk in clusters.chunked(5)) {
            val r = api.clustersWithResources(id, chunk.toMap(), skip)
            val fetched = r["clusters"]?.jsonObject?.get("fetched")?.jsonArray.orEmpty()
            db.withTransaction {
                for (cl in fetched) for (it in cl.jsonObject["items"]?.jsonArray.orEmpty()) {
                    val o = it.jsonObject
                    val itemId = o.str("itemId") ?: continue
                    val path = o.str("id") ?: continue
                    if (items.get(itemId) != null) continue
                    val albums = (o["albums"] as? JsonArray)?.mapNotNull { a -> (a as? JsonPrimitive)?.content }.orEmpty()
                    added += enqueue(itemId, ItemIds.normalizePath(path), albums, Source.BACKFILL, skip)
                }
            }
        }
        log.info("SYNC", "backfill since cutoff: clusters=${clusters.size}, queued=$added")
        return added
    }

    private suspend fun enqueue(itemId: String, path: String, albums: List<String>, source: String, skip: Boolean): Int {
        val etimeMs = ItemIds.etimeMs(itemId) ?: return 0
        if (etimeMs < cutoffMs) return 0
        if (skip && albums.any { it.contains("screenshots") }) return 0
        val row = ItemEntity(
            itemId = itemId, path = path, name = ItemIds.fileName(path),
            source = source, etime = etimeMs / 1000,
        )
        return if (items.insertIgnore(row) != -1L) 1 else 0
    }

    private fun now() = System.currentTimeMillis()
}
