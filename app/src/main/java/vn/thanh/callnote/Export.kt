package vn.thanh.callnote

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Đóng gói nội dung cuộc gọi để gửi sang Telegram (Hermes) hoặc Claude. */
object Export {
    private val timeFmt = SimpleDateFormat("HH:mm", Locale("vi"))
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))
    private val fileFmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 60) "${s / 60} phút ${s % 60} giây" else "$s giây"
    }

    fun build(prefs: Prefs, calls: List<CallRecord>, title: String): String {
        val sorted = calls.sortedBy { it.callTime }
        val sb = StringBuilder()
        sb.append(prefs.exportHeader.trim()).append("\n\n")
        sb.append("CUỘC GỌI $title — ${sorted.size} cuộc\n")
        sorted.forEachIndexed { i, c ->
            sb.append("\n=== ${i + 1}. ${c.contact} — ${dateFmt.format(Date(c.callTime))} ${timeFmt.format(Date(c.callTime))}")
            sb.append(" (${duration(c.durationMs)}) ===\n")
            sb.append(c.transcript.trim()).append('\n')
        }
        return sb.toString()
    }

    /** Mở bảng chia sẻ; nội dung dài sẽ gửi dạng file .txt. */
    fun share(ctx: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        if (text.length <= 3500) {
            intent.putExtra(Intent.EXTRA_TEXT, text)
        } else {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "cuoc_goi_${fileFmt.format(Date())}.txt")
            f.writeText(text, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.putExtra(Intent.EXTRA_TEXT, "Lập danh sách việc cần làm từ file cuộc gọi đính kèm.")
            intent.clipData = ClipData.newRawUri("calls", uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Gửi tổng hợp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
