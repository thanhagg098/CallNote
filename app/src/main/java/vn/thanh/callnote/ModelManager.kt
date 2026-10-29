package vn.thanh.callnote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Tải hoặc nhập file mô hình chép lời (định dạng ggml của whisper.cpp). */
object ModelManager {

    data class Progress(val running: Boolean, val done: Long, val total: Long, val failed: Boolean)

    fun startDownload(ctx: Context, prefs: Prefs) {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        if (prefs.downloadId >= 0) dm.remove(prefs.downloadId)
        val tmp = File(prefs.modelFile.parentFile, "download.bin")
        tmp.delete()
        val req = DownloadManager.Request(Uri.parse(prefs.modelUrl))
            .setTitle("Mô hình chép lời tiếng Việt")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(ctx, "models", "download.bin")
            .setAllowedOverMetered(true)
        prefs.downloadId = dm.enqueue(req)
    }

    /** Kiểm tra tiến độ; khi tải xong thì thay file mô hình. */
    fun poll(ctx: Context, prefs: Prefs): Progress? {
        val id = prefs.downloadId
        if (id < 0) return null
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) { prefs.downloadId = -1; return null }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val tmp = File(prefs.modelFile.parentFile, "download.bin")
                    if (tmp.exists()) {
                        prefs.modelFile.delete()
                        tmp.renameTo(prefs.modelFile)
                        prefs.modelName = prefs.modelUrl.substringAfterLast('/')
                    }
                    prefs.downloadId = -1
                    Progress(false, done, total, false)
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.downloadId = -1
                    Progress(false, done, total, true)
                }
                else -> Progress(true, done, total, false)
            }
        }
        return null
    }

    /** Nhập file mô hình người dùng chọn từ bộ nhớ máy. */
    fun importFrom(ctx: Context, prefs: Prefs, uri: Uri) {
        var name = "model.bin"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val tmp = File(prefs.modelFile.parentFile, "import.bin")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { input.copyTo(it, 1 shl 20) }
        }
        require(tmp.length() > 10_000_000L) { tmp.delete(); "File quá nhỏ, không phải mô hình whisper" }
        prefs.modelFile.delete()
        tmp.renameTo(prefs.modelFile)
        prefs.modelName = name
    }
}
