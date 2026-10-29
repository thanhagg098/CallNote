package vn.thanh.callnote

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object Status {
    const val PENDING = 0
    const val PROCESSING = 1
    const val DONE = 2
    const val ERROR = 3
    const val SKIPPED = 4
}

data class CallRecord(
    val id: Long,
    val mediaId: Long,
    val uri: String,
    val fileName: String,
    val contact: String,
    val callTime: Long,
    val durationMs: Long,
    val status: Int,
    val transcript: String,
    val error: String,
    val sent: Boolean,
)

/** Bảng dữ liệu cuộc gọi lưu trong máy (SQLite). */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "callnote.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE calls(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                media_id INTEGER UNIQUE,
                uri TEXT NOT NULL,
                file_name TEXT NOT NULL,
                contact TEXT NOT NULL,
                call_time INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                status INTEGER NOT NULL DEFAULT 0,
                transcript TEXT NOT NULL DEFAULT '',
                error TEXT NOT NULL DEFAULT '',
                processed_at INTEGER NOT NULL DEFAULT 0,
                sent INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_time ON calls(call_time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun knownMediaIds(): Set<Long> {
        val out = HashSet<Long>()
        readableDatabase.rawQuery("SELECT media_id FROM calls", null).use {
            while (it.moveToNext()) out.add(it.getLong(0))
        }
        return out
    }

    fun insert(r: CallRecord) {
        val v = ContentValues().apply {
            put("media_id", r.mediaId); put("uri", r.uri); put("file_name", r.fileName)
            put("contact", r.contact); put("call_time", r.callTime); put("duration_ms", r.durationMs)
            put("status", r.status)
        }
        writableDatabase.insertWithOnConflict("calls", null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun setStatus(id: Long, status: Int, transcript: String? = null, error: String? = null) {
        val v = ContentValues().apply {
            put("status", status)
            transcript?.let { put("transcript", it) }
            error?.let { put("error", it) }
            if (status == Status.DONE) put("processed_at", System.currentTimeMillis())
        }
        writableDatabase.update("calls", v, "id=?", arrayOf(id.toString()))
    }

    fun resetStuck() {
        writableDatabase.execSQL("UPDATE calls SET status=${Status.PENDING} WHERE status=${Status.PROCESSING}")
    }

    fun markSent(ids: List<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.execSQL("UPDATE calls SET sent=1 WHERE id IN (${ids.joinToString(",")})")
    }

    fun pendingBetween(from: Long, to: Long): List<CallRecord> =
        query("WHERE status=${Status.PENDING} AND call_time>=? AND call_time<? ORDER BY call_time ASC", from.toString(), to.toString())

    fun between(from: Long, to: Long): List<CallRecord> =
        query("WHERE call_time>=? AND call_time<? ORDER BY call_time DESC", from.toString(), to.toString())

    fun byId(id: Long): CallRecord? = query("WHERE id=?", id.toString()).firstOrNull()

    private fun query(where: String, vararg args: String): List<CallRecord> {
        val out = ArrayList<CallRecord>()
        readableDatabase.rawQuery("SELECT * FROM calls $where", arrayOf(*args)).use { c ->
            while (c.moveToNext()) out.add(c.toRecord())
        }
        return out
    }

    private fun Cursor.toRecord() = CallRecord(
        id = getLong(getColumnIndexOrThrow("id")),
        mediaId = getLong(getColumnIndexOrThrow("media_id")),
        uri = getString(getColumnIndexOrThrow("uri")),
        fileName = getString(getColumnIndexOrThrow("file_name")),
        contact = getString(getColumnIndexOrThrow("contact")),
        callTime = getLong(getColumnIndexOrThrow("call_time")),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        status = getInt(getColumnIndexOrThrow("status")),
        transcript = getString(getColumnIndexOrThrow("transcript")),
        error = getString(getColumnIndexOrThrow("error")),
        sent = getInt(getColumnIndexOrThrow("sent")) == 1,
    )

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }
    }
}
