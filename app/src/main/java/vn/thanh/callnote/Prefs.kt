package vn.thanh.callnote

import android.content.Context
import java.io.File

/** Cài đặt của app. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("callnote", Context.MODE_PRIVATE)
    private val modelDir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "models")

    val modelFile: File get() = File(modelDir.apply { mkdirs() }, "model.bin")
    val modelReady: Boolean get() = modelFile.exists() && modelFile.length() > 10_000_000L

    var modelUrl: String
        get() = sp.getString("model_url", DEFAULT_MODEL_URL) ?: DEFAULT_MODEL_URL
        set(v) = sp.edit().putString("model_url", v.trim()).apply()

    var modelName: String
        get() = sp.getString("model_name", "") ?: ""
        set(v) = sp.edit().putString("model_name", v).apply()

    var downloadId: Long
        get() = sp.getLong("download_id", -1L)
        set(v) = sp.edit().putLong("download_id", v).apply()

    /** Chỉ xử lý các cuộc gọi sau mốc thời gian này (tránh chạy lại hàng nghìn bản ghi cũ). */
    var cutoffTime: Long
        get() {
            val v = sp.getLong("cutoff", 0L)
            if (v == 0L) {
                val d = System.currentTimeMillis() - 24L * 3600_000
                sp.edit().putLong("cutoff", d).apply(); return d
            }
            return v
        }
        set(v) = sp.edit().putLong("cutoff", v).apply()

    var minSeconds: Int
        get() = sp.getInt("min_seconds", 20)
        set(v) = sp.edit().putInt("min_seconds", v).apply()

    var onlyWhenCharging: Boolean
        get() = sp.getBoolean("charging", false)
        set(v) = sp.edit().putBoolean("charging", v).apply()

    var threads: Int
        get() = sp.getInt("threads", 4)
        set(v) = sp.edit().putInt("threads", v.coerceIn(1, 8)).apply()

    var folder: String
        get() = sp.getString("folder", "Recordings/Call") ?: "Recordings/Call"
        set(v) = sp.edit().putString("folder", v.trim().trim('/')).apply()

    var vocabPrompt: String
        get() = sp.getString("prompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT
        set(v) = sp.edit().putString("prompt", v).apply()

    var dailyReminder: Boolean
        get() = sp.getBoolean("daily", true)
        set(v) = sp.edit().putBoolean("daily", v).apply()

    var reminderHour: Int
        get() = sp.getInt("daily_hour", 21)
        set(v) = sp.edit().putInt("daily_hour", v.coerceIn(0, 23)).apply()

    var exportHeader: String
        get() = sp.getString("export_header", DEFAULT_HEADER) ?: DEFAULT_HEADER
        set(v) = sp.edit().putString("export_header", v).apply()

    companion object {
        const val DEFAULT_MODEL_URL =
            "https://github.com/thanhagg098/CallNote/releases/download/model/ggml-phowhisper-small-q5_1.bin"
        const val DEFAULT_PROMPT =
            "Cuộc gọi công việc bằng tiếng Việt. Viettel, An Giang, Phú Quốc, hạ tầng, trạm BTS, cáp quang, công văn."
        const val DEFAULT_HEADER =
            "Dưới đây là nội dung các cuộc gọi đã được chép lời tự động (có thể sai chính tả). " +
                "Hãy lập DANH SÁCH VIỆC CẦN LÀM: mỗi việc ghi rõ nội dung, người liên quan, hạn chót (nếu có), " +
                "mức ưu tiên. Bỏ qua cuộc gọi không có việc. Cuối cùng liệt kê các việc cần gọi lại / nhắc lại."
    }
}
