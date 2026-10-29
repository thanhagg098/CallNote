package vn.thanh.callnote

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

/** Tìm file ghi âm cuộc gọi mới trong thư mục Recordings/Call qua MediaStore. */
object Recordings {
    // Ví dụ tên file Samsung: "Cuộc gọi Nguyễn Hữu Trình_261028_160653.m4a"
    private val NAME_RE = Regex("""^(?:Cuộc gọi|Call recording|Call)\s*(.*?)_(\d{6})_(\d{6})$""", RegexOption.IGNORE_CASE)

    data class Parsed(val contact: String, val time: Long?)

    fun parseName(fileName: String): Parsed {
        val base = Normalizer.normalize(fileName.substringBeforeLast('.'), Normalizer.Form.NFC)
        val m = NAME_RE.find(base) ?: return Parsed(base, null)
        val (who, d, t) = m.destructured
        val time = runCatching {
            SimpleDateFormat("yyMMddHHmmss", Locale.US).parse(d + t)?.time
        }.getOrNull()
        return Parsed(who.ifBlank { "Không rõ" }, time)
    }

    /** Quét và thêm các bản ghi mới vào DB. Trả về số bản ghi mới. */
    fun scan(ctx: Context, prefs: Prefs, db: Db): Int {
        val known = db.knownMediaIds()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val sel = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.DATE_ADDED} >= ?"
        val args = arrayOf("%${prefs.folder}%", (prefs.cutoffTime / 1000).toString())
        var added = 0
        ctx.contentResolver.query(uri, proj, sel, args, "${MediaStore.Audio.Media.DATE_ADDED} ASC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                if (id in known) continue
                val name = c.getString(iName) ?: continue
                val dur = c.getLong(iDur)
                val parsed = parseName(name)
                val time = parsed.time ?: (c.getLong(iAdded) * 1000)
                if (time < prefs.cutoffTime) continue
                val tooShort = dur in 1 until prefs.minSeconds * 1000L
                db.insert(
                    CallRecord(
                        id = 0, mediaId = id,
                        uri = ContentUris.withAppendedId(uri, id).toString(),
                        fileName = name, contact = parsed.contact, callTime = time,
                        durationMs = dur,
                        status = if (tooShort) Status.SKIPPED else Status.PENDING,
                        transcript = "", error = if (tooShort) "Cuộc gọi quá ngắn" else "", sent = false,
                    )
                )
                added++
            }
        }
        return added
    }
}
