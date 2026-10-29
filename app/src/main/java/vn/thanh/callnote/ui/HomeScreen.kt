package vn.thanh.callnote.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import vn.thanh.callnote.CallRecord
import vn.thanh.callnote.DayRange
import vn.thanh.callnote.Db
import vn.thanh.callnote.Export
import vn.thanh.callnote.MainActivity
import vn.thanh.callnote.Prefs
import vn.thanh.callnote.Recordings
import vn.thanh.callnote.Scheduler
import vn.thanh.callnote.Status
import vn.thanh.callnote.TranscribeWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun dayEnd(start: Long): Long =
    Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

private fun shiftDay(start: Long, by: Int): Long =
    Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_MONTH, by) }.timeInMillis

/** Gửi bản tổng hợp cả ngày (dùng ở màn hình chính và khi bấm thông báo). */
fun shareDay(ctx: android.content.Context, dayStart: Long) {
    val db = Db.get(ctx)
    val calls = db.between(dayStart, dayEnd(dayStart))
    val done = calls.filter { it.status == Status.DONE }
    if (done.isEmpty()) {
        Toast.makeText(ctx, "Ngày này chưa có cuộc nào được chép lời", Toast.LENGTH_SHORT).show(); return
    }
    Export.share(ctx, Export.buildDay(Prefs(ctx), calls, dayStart))
    db.markSent(done.map { it.id })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val db = remember { Db.get(ctx) }
    var day by remember { mutableLongStateOf(DayRange.dayStart(0)) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var hasPerm by remember { mutableStateOf(TranscribeWorker.hasAudioPermission(ctx)) }
    var pickDate by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = TranscribeWorker.hasAudioPermission(ctx)
        tick++
    }
    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(MainActivity.PERMISSIONS) }

    // Mở app / đổi ngày: đọc danh sách file ghi âm của ngày đó (nhanh, không chép lời).
    // Sau đó chỉ đọc lại bảng mỗi 2 giây để cập nhật tiến độ khi đang chép lời.
    LaunchedEffect(day, tick, hasPerm) {
        val to = dayEnd(day)
        if (hasPerm) withContext(Dispatchers.IO) { runCatching { Recordings.scanDay(ctx, prefs, db, day, to) } }
        while (true) {
            calls = withContext(Dispatchers.IO) { db.between(day, to) }
            delay(2000)
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day + TimeZone.getDefault().getOffset(day))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
                        day = Calendar.getInstance().apply {
                            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
                        }.timeInMillis.coerceAtMost(DayRange.dayStart(0))
                    }
                    pickDate = false
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Huỷ") } }
        ) { DatePicker(state) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tổng hợp cuộc gọi") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Cài đặt") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!hasPerm) {
                Banner("App cần quyền đọc file ghi âm cuộc gọi trong máy.", "Cấp quyền") {
                    permLauncher.launch(MainActivity.PERMISSIONS)
                }
            } else if (!prefs.modelReady) {
                Banner("Chưa có mô hình chép lời. Vào Cài đặt để tải (khoảng 190 MB, chỉ tải 1 lần).", "Mở Cài đặt", onSettings)
            }

            // Chọn ngày
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { day = shiftDay(day, -1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ngày trước") }
                Text(
                    Export.dayLabel(day).replaceFirstChar { it.uppercase() },
                    Modifier.weight(1f).clickable { pickDate = true },
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { pickDate = true }) { Icon(Icons.Default.CalendarMonth, "Chọn ngày") }
                IconButton(enabled = day < DayRange.dayStart(0), onClick = { day = shiftDay(day, 1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Ngày sau")
                }
            }

            DaySummary(calls,
                onTranscribe = {
                    Scheduler.transcribeDay(ctx, day, dayEnd(day))
                    Toast.makeText(ctx, "Bắt đầu chép lời. Có thể thoát app, xong sẽ có thông báo.", Toast.LENGTH_LONG).show()
                },
                onShare = { shareDay(ctx, day) },
                modelReady = prefs.modelReady,
            )

            if (calls.isEmpty()) {
                Text(
                    "Không có file ghi âm cuộc gọi nào trong ngày này.\n(App đọc thư mục \"${prefs.folder}\" — đổi trong Cài đặt nếu cần.)",
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calls.sortedBy { it.callTime }, key = { it.id }) { c -> CallCard(c) { onOpen(c.id) } }
            }
        }
    }
}

@Composable
private fun DaySummary(calls: List<CallRecord>, onTranscribe: () -> Unit, onShare: () -> Unit, modelReady: Boolean) {
    if (calls.isEmpty()) return
    val o = Export.overview(calls)
    val processing = calls.any { it.status == Status.PROCESSING }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("${o.total} cuộc · ${Export.minutes(o.totalMs)} · đã chép lời ${o.done}/${o.total}", fontWeight = FontWeight.SemiBold)
            if (o.topContacts.isNotEmpty()) Text(
                "Gọi nhiều: " + o.topContacts.joinToString(", ") { "${it.first} (${it.second})" },
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (processing) {
                val frac = if (o.total > 0) o.done.toFloat() / o.total else 0f
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("Đang chép lời… (có thể thoát app)", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (o.pending > 0 && !processing) {
                    Button(enabled = modelReady, onClick = onTranscribe) { Text("Chép lời ${o.pending} cuộc") }
                    Spacer(Modifier.width(8.dp))
                }
                val shareBtn: @Composable () -> Unit = {
                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Gửi tổng hợp")
                }
                if (o.pending == 0) Button(enabled = o.done > 0, onClick = onShare, content = { shareBtn() })
                else OutlinedButton(enabled = o.done > 0, onClick = onShare, content = { shareBtn() })
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(text)
            OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale("vi"))

fun statusLabel(s: Int) = when (s) {
    Status.PENDING -> "Chưa chép lời"
    Status.PROCESSING -> "Đang chép lời…"
    Status.DONE -> "Đã chép lời"
    Status.ERROR -> "Lỗi"
    else -> "Bỏ qua (quá ngắn)"
}

@Composable
private fun CallCard(c: CallRecord, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(c.contact, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeFmt.format(Date(c.callTime)), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${Export.duration(c.durationMs)} · ${statusLabel(c.status)}" + if (c.sent) " · đã gửi" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (c.status == Status.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (c.transcript.isNotBlank()) {
                Text(
                    c.transcript.lineSequence().joinToString(" ") { it.substringAfter("] ") },
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
