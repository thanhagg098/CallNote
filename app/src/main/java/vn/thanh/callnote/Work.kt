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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

    fun post(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}

object Scheduler {
    private const val PERIODIC = "transcribe_periodic"
    private const val NOW = "transcribe_now"
    private const val DAILY = "daily_reminder"

    fun schedule(ctx: Context) {
        val p = Prefs(ctx)
        val c = Constraints.Builder()
            .setRequiresCharging(p.onlyWhenCharging)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<TranscribeWorker>(15, TimeUnit.MINUTES).setConstraints(c).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)

        if (p.dailyReminder) {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, p.reminderHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val daily = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, daily)
        } else {
            WorkManager.getInstance(ctx).cancelUniqueWork(DAILY)
        }
    }

    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<TranscribeWorker>().build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }
}

/** Quét bản ghi mới và chép lời offline từng cuộc. */
class TranscribeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val db = Db.get(ctx)
        db.resetStuck()
        if (!hasAudioPermission(ctx)) return@withContext Result.success()
        runCatching { Recordings.scan(ctx, prefs, db) }
        if (!prefs.modelReady) return@withContext Result.success()

        val todo = db.pending()
        if (todo.isEmpty()) return@withContext Result.success()
        runCatching { setForeground(foregroundInfo(0, todo.size)) }

        val model = WhisperLib.initContext(prefs.modelFile.absolutePath)
        if (model == 0L) {
            Notif.post(ctx, 9, "Lỗi mô hình", "Không nạp được mô hình chép lời. Hãy tải lại trong Cài đặt.")
            return@withContext Result.success()
        }
        try {
            todo.forEachIndexed { i, rec ->
                if (isStopped) return@withContext Result.retry()
                runCatching { setForeground(foregroundInfo(i + 1, todo.size)) }
                db.setStatus(rec.id, Status.PROCESSING)
                try {
                    val samples = AudioDecoder.decode(ctx, Uri.parse(rec.uri))
                    if (samples.size < 16000 * 2) {
                        db.setStatus(rec.id, Status.SKIPPED, error = "Âm thanh quá ngắn")
                    } else {
                        val text = WhisperLib.transcribeText(model, samples, "vi", prefs.threads, prefs.vocabPrompt)
                        db.setStatus(rec.id, Status.DONE, transcript = text.ifBlank { "(không nhận ra lời nói)" }, error = "")
                        Notif.post(
                            ctx, (rec.id % 100000).toInt() + 100,
                            "Đã chép lời: ${rec.contact}",
                            text.lineSequence().take(3).joinToString(" ") { it.substringAfter("] ") }.take(300)
                        )
                    }
                } catch (e: Throwable) {
                    db.setStatus(rec.id, Status.ERROR, error = e.message ?: e.javaClass.simpleName)
                }
            }
        } finally {
            WhisperLib.freeContext(model)
        }
        Result.success()
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, Notif.CH_WORK)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Đang chép lời cuộc gọi")
            .setContentText("$done / $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(7, n)
    }

    companion object {
        fun hasAudioPermission(ctx: Context): Boolean {
            val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/** Nhắc gửi tổng hợp cuộc gọi trong ngày. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val db = Db.get(applicationContext)
        val (from, to) = DayRange.today()
        val calls = db.between(from, to).filter { it.status == Status.DONE }
        if (calls.isNotEmpty()) {
            val unsent = calls.count { !it.sent }
            Notif.post(
                applicationContext, 8, "Tổng hợp cuộc gọi hôm nay",
                "${calls.size} cuộc đã chép lời ($unsent chưa gửi). Bấm để mở và gửi cho Hermes/Claude lập danh sách việc."
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
