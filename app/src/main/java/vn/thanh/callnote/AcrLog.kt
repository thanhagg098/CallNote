package vn.thanh.callnote

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nhật ký ngắn (lưu trong máy) về việc bật/tắt Cube ACR Helper, để kiểm tra khi không ghi được. */
object AcrLog {
    private const val KEY = "acr_log"
    private const val MAX_LINES = 60

    @Synchronized
    fun add(ctx: Context, msg: String) {
        val sp = ctx.getSharedPreferences("callnote", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
        val lines = (sp.getString(KEY, "") ?: "").lines().filter { it.isNotBlank() }
        val next = (listOf("$time  $msg") + lines).take(MAX_LINES)
        sp.edit().putString(KEY, next.joinToString("\n")).apply()
    }

    fun read(ctx: Context): String =
        ctx.getSharedPreferences("callnote", Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun clear(ctx: Context) {
        ctx.getSharedPreferences("callnote", Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
