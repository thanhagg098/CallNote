package vn.thanh.callnote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Notif {
    const val CH_WORK = "work"
    const val CH_RESULT = "result"

    fun setup(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WORK, "Đang chép lời", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, "Kết quả cuộc gọi", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun post(ctx: Context, id: Int, title: String, text: String, intent: PendingIntent? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent ?: openApp(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

object Scheduler {
    /** Tên việc chạy nền 15 phút của bản cũ — giữ để huỷ khi cài đè. */
    private const val OLD_PERIODIC = "transcribe_periodic"
    private const val NOW = "transcribe_now"
    private const val DAILY = "daily_reminder"

    /** Không còn quét nền. Chỉ đặt (hoặc huỷ) lời nhắc tổng hợp hằng ngày. */
    fun setup(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(OLD_PERIODIC)
        val p = Prefs(ctx)
        if (p.dailyReminder) {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, p.reminderHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val daily = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build()
            wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, daily)
        } else {
            wm.cancelUniqueWork(DAILY)
        }
    }

    /** Chép lời các cuộc chưa chép trong ngày [from, to), xong thì báo để gửi tổng hợp. */
    fun transcribeDay(ctx: Context, from: Long, to: Long) =
        enqueue(ctx, workDataOf(TranscribeWorker.KEY_MODE to TranscribeWorker.MODE_DAY,
            TranscribeWorker.KEY_FROM to from, TranscribeWorker.KEY_TO to to))

    /** Chép lời lại đúng 1 cuộc. */
    fun transcribe(ctx: Context, onlyId: Long) =
        enqueue(ctx, workDataOf(TranscribeWorker.KEY_MODE to TranscribeWorker.MODE_ONE, TranscribeWorker.KEY_ID to onlyId))

    private fun enqueue(ctx: Context, data: androidx.work.Data) {
        val req = OneTimeWorkRequestBuilder<TranscribeWorker>().setInputData(data).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}

/** Chép lời offline các cuộc người dùng yêu cầu (1 ngày hoặc 1 cuộc). Không tự chạy. */
class TranscribeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val db = Db.get(ctx)
        db.resetStuck()
        if (!prefs.modelReady) {
            Notif.post(ctx, 9, "Chưa có mô hình", "Vào Cài đặt → Tải mô hình trước khi chép lời.")
            return@withContext Result.success()
        }

        val mode = inputData.getString(KEY_MODE) ?: MODE_DAY
        val from = inputData.getLong(KEY_FROM, 0L)
        val to = inputData.getLong(KEY_TO, 0L)
        val todo = when (mode) {
            MODE_ONE -> listOfNotNull(db.byId(inputData.getLong(KEY_ID, -1L))?.takeIf { it.status == Status.PENDING })
            else -> {
                if (hasAudioPermission(ctx)) runCatching { Recordings.scanDay(ctx, prefs, db, from, to) }
                db.retryErrors(from, to)
                db.pendingBetween(from, to)
            }
        }
        if (todo.isEmpty()) {
            if (mode == MODE_DAY) notifyDayDone(ctx, db, from, to)
            return@withContext Result.success()
        }
        runCatching { setForeground(foregroundInfo(0, todo.size)) }

        val model = WhisperLib.initContext(prefs.modelFile.absolutePath)
        if (model == 0L) {
            Notif.post(ctx, 9, "Lỗi mô hình", "Không nạp được mô hình chép lời. Hãy tải lại trong Cài đặt.")
            return@withContext Result.success()
        }
        try {
            todo.forEachIndexed { i, rec ->
                if (isStopped) return@withContext Result.success()
                runCatching { setForeground(foregroundInfo(i, todo.size)) }
                db.setStatus(rec.id, Status.PROCESSING)
                try {
                    val samples = AudioDecoder.decode(ctx, Uri.parse(rec.uri))
                    if (samples.size < 16000 * 2) {
                        db.setStatus(rec.id, Status.SKIPPED, error = "Âm thanh quá ngắn")
                    } else {
                        val text = WhisperLib.transcribeText(model, samples, "vi", prefs.threads, prefs.vocabPrompt, if (prefs.highQuality) 5 else 1)
                        db.setStatus(rec.id, Status.DONE, transcript = text.ifBlank { "(không nhận ra lời nói)" }, error = "")
                    }
                } catch (e: Throwable) {
                    db.setStatus(rec.id, Status.ERROR, error = e.message ?: e.javaClass.simpleName)
                }
            }
        } finally {
            WhisperLib.freeContext(model)
        }
        if (mode == MODE_DAY) notifyDayDone(ctx, db, from, to)
        Result.success()
    }

    /** Báo xong; bấm thông báo là mở ngay bảng gửi tổng hợp của ngày đó. */
    private fun notifyDayDone(ctx: Context, db: Db, from: Long, to: Long) {
        val calls = db.between(from, to)
        val done = calls.count { it.status == Status.DONE }
        if (done == 0) return
        val err = calls.count { it.status == Status.ERROR }
        val pi = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHARE_DAY, from)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        Notif.post(
            ctx, 8, "Đã tổng hợp cuộc gọi ${Export.dayLabel(from)}",
            "$done cuộc đã chép lời" + (if (err > 0) ", $err cuộc lỗi" else "") + ". Bấm để gửi cho Hermes/Claude.",
            pi
        )
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, Notif.CH_WORK)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Đang chép lời cuộc gọi")
            .setContentText("${done + 1} / $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(7, n)
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_ID = "id"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val MODE_DAY = "day"
        const val MODE_ONE = "one"

        fun hasAudioPermission(ctx: Context): Boolean {
            val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/** Mỗi tối: đếm cuộc ghi âm trong ngày (chỉ đọc danh sách file, không chép lời) và nhắc tổng hợp. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val db = Db.get(ctx)
        val (from, to) = DayRange.today()
        if (TranscribeWorker.hasAudioPermission(ctx)) runCatching { Recordings.scanDay(ctx, Prefs(ctx), db, from, to) }
        val calls = db.between(from, to).filter { it.status != Status.SKIPPED }
        if (calls.isNotEmpty()) {
            val mins = calls.sumOf { it.durationMs } / 60000
            Notif.post(
                ctx, 8, "Cuộc gọi hôm nay: ${calls.size} cuộc, $mins phút",
                "Bấm để mở app và tổng hợp tình hình trong ngày."
            )
        }
        return Result.success()
    }
}

object DayRange {
    fun dayStart(offsetDays: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, offsetDays)
    }.timeInMillis

    fun today() = dayStart(0) to dayStart(1)
}
