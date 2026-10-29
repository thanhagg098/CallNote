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

    private val dayFmt = SimpleDateFormat("EEEE dd/MM/yyyy", Locale("vi"))

    /** "hôm nay (Thứ Năm 29/10/2026)" / "hôm qua (…)" / "Thứ Hai 26/10/2026". */
    fun dayLabel(dayStart: Long): String {
        val d = dayFmt.format(Date(dayStart)).replaceFirstChar { it.uppercase() }
        return when (dayStart) {
            DayRange.dayStart(0) -> "hôm nay ($d)"
            DayRange.dayStart(-1) -> "hôm qua ($d)"
            else -> d
        }
    }

    data class Overview(
        val total: Int, val totalMs: Long, val done: Int, val pending: Int, val skipped: Int, val error: Int,
        val topContacts: List<Pair<String, Int>>,
    )

    fun overview(calls: List<CallRecord>): Overview {
        val real = calls.filter { it.status != Status.SKIPPED }
        return Overview(
            total = real.size,
            totalMs = real.sumOf { it.durationMs },
            done = calls.count { it.status == Status.DONE },
            pending = calls.count { it.status == Status.PENDING || it.status == Status.PROCESSING },
            skipped = calls.count { it.status == Status.SKIPPED },
            error = calls.count { it.status == Status.ERROR },
            topContacts = real.groupingBy { it.contact }.eachCount().entries
                .sortedByDescending { it.value }.filter { it.value > 1 }.take(3).map { it.key to it.value },
        )
    }

    fun minutes(ms: Long): String = "${(ms + 30_000) / 60_000} phút"

    /** Bản tổng hợp cả ngày: tổng quan + bảng cuộc gọi + lời từng cuộc. */
    fun buildDay(prefs: Prefs, calls: List<CallRecord>, dayStart: Long): String {
        val sorted = calls.sortedBy { it.callTime }
        val o = overview(sorted)
        val sb = StringBuilder()
        sb.append(prefs.exportHeader.trim()).append("\n\n")
        sb.append("TÌNH HÌNH CUỘC GỌI ${dayLabel(dayStart).uppercase()}\n")
        sb.append("- Tổng: ${o.total} cuộc, ${minutes(o.totalMs)}")
        if (o.skipped > 0) sb.append(" (bỏ qua ${o.skipped} cuộc quá ngắn)")
        sb.append('\n')
        if (o.topContacts.isNotEmpty())
            sb.append("- Gọi nhiều nhất: ").append(o.topContacts.joinToString(", ") { "${it.first} (${it.second} cuộc)" }).append('\n')
        if (o.pending + o.error > 0) sb.append("- Chưa có lời: ${o.pending + o.error} cuộc (chưa chép hoặc lỗi)\n")
        sb.append("\nDANH SÁCH:\n")
        sorted.filter { it.status != Status.SKIPPED }.forEachIndexed { i, c ->
            sb.append("${i + 1}. ${timeFmt.format(Date(c.callTime))} ${c.contact} (${duration(c.durationMs)})\n")
        }
        var n = 0
        sorted.filter { it.status == Status.DONE }.forEach { c ->
            n++
            sb.append("\n=== ${n}. ${c.contact} — ${timeFmt.format(Date(c.callTime))} (${duration(c.durationMs)}) ===\n")
            sb.append(c.transcript.trim()).append('\n')
        }
        return sb.toString()
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
            val f = File(dir, "tong_hop_cuoc_goi_${fileFmt.format(Date())}.txt")
            f.writeText(text, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.putExtra(Intent.EXTRA_TEXT, "Tổng hợp tình hình và lập danh sách việc cần làm từ file cuộc gọi đính kèm.")
            intent.clipData = ClipData.newRawUri("calls", uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Gửi tổng hợp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
