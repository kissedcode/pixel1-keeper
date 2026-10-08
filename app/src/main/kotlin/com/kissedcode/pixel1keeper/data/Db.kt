package com.kissedcode.pixel1keeper.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object Status {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val ON_DEVICE = "ON_DEVICE"
    const val UPLOADED_ASSUMED = "UPLOADED_ASSUMED"
    const val DELETED = "DELETED"
    const val SKIPPED = "SKIPPED"
    const val FAILED = "FAILED"
}

object Source {
    const val DELTA = "DELTA"
    const val BACKFILL = "BACKFILL"
}

@Entity(tableName = "items", indices = [Index("status", "source", "etime")])
data class ItemEntity(
    @PrimaryKey val itemId: String,
    val path: String,
    val name: String,
    val source: String,
    /** Дата съёмки, секунды. */
    val etime: Long,
    val size: Long? = null,
    val mime: String? = null,
    val status: String = Status.QUEUED,
    val mediaUri: String? = null,
    val downloadedBytes: Long = 0,
    val onDeviceAt: Long? = null,
    val statusChangedAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val level: String,
    val type: String,
    val message: String,
)

@Entity(tableName = "traffic_marks")
data class TrafficMark(
    @PrimaryKey val ts: Long,
    /** Накопленный Wi-Fi tx Google Photos с момента первой отметки. */
    val photosTxBytes: Long,
    val onDeviceBytesTotal: Long,
)

data class Counts(val n: Int, val bytes: Long?)

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("SELECT * FROM items WHERE itemId = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE status IN ('QUEUED','DOWNLOADING') ORDER BY etime ASC LIMIT :limit")
    suspend fun downloadQueue(limit: Int = 500): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status = 'FAILED' AND statusChangedAt < :before ORDER BY etime ASC")
    suspend fun failedBefore(before: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status IN ('ON_DEVICE','UPLOADED_ASSUMED') ORDER BY onDeviceAt ASC")
    suspend fun onDevice(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status = 'DOWNLOADING'")
    suspend fun downloading(): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items WHERE status = :status")
    fun countFlow(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE status IN ('ON_DEVICE','UPLOADED_ASSUMED','DELETED')")
    fun transferredFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE status IN ('QUEUED','DOWNLOADING')")
    fun queuedFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE status IN ('ON_DEVICE','UPLOADED_ASSUMED')")
    fun onDeviceFlow(): Flow<Int>

    @Query("SELECT COUNT(*) AS n, SUM(size) AS bytes FROM items WHERE status IN ('ON_DEVICE','UPLOADED_ASSUMED')")
    suspend fun onDeviceCounts(): Counts

    @Query("SELECT COUNT(*) AS n, SUM(size) AS bytes FROM items WHERE onDeviceAt >= :since")
    suspend fun downloadedSince(since: Long): Counts

    @Query("SELECT COUNT(*) AS n, SUM(size) AS bytes FROM items WHERE status = 'DELETED' AND statusChangedAt >= :since")
    suspend fun deletedSince(since: Long): Counts

    @Query("SELECT COUNT(*) AS n, SUM(size) AS bytes FROM items WHERE status = 'FAILED'")
    suspend fun failedCounts(): Counts

    @Query("SELECT COALESCE(SUM(downloadedBytes),0) FROM items WHERE status = 'DOWNLOADING'")
    suspend fun bytesInFlight(): Long

    @Query("SELECT COALESCE(SUM(size),0) FROM items WHERE onDeviceAt IS NOT NULL")
    suspend fun everOnDeviceBytes(): Long
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(e: EventEntity)

    @Query("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 500)")
    suspend fun trim()

    @Query("SELECT * FROM events ORDER BY id DESC LIMIT 500")
    fun recent(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE ts >= :since ORDER BY id ASC")
    suspend fun since(since: Long): List<EventEntity>
}

@Dao
interface TrafficDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: TrafficMark)

    @Query("SELECT * FROM traffic_marks ORDER BY ts ASC LIMIT 1")
    suspend fun first(): TrafficMark?

    @Query("SELECT * FROM traffic_marks WHERE ts <= :ts ORDER BY ts DESC LIMIT 1")
    suspend fun atOrBefore(ts: Long): TrafficMark?

    @Query("DELETE FROM traffic_marks WHERE ts < :before AND ts <> (SELECT MIN(ts) FROM traffic_marks)")
    suspend fun trim(before: Long)
}

@Database(entities = [ItemEntity::class, EventEntity::class, TrafficMark::class], version = 1, exportSchema = true)
abstract class KeeperDb : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun eventDao(): EventDao
    abstract fun trafficDao(): TrafficDao

    companion object {
        fun create(context: Context): KeeperDb =
            Room.databaseBuilder(context, KeeperDb::class.java, "keeper.db").build()
    }
}
