package vn.thanh.callnote.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import vn.thanh.callnote.CallRecord
import vn.thanh.callnote.Db
import vn.thanh.callnote.DayRange
import vn.thanh.callnote.Export
import vn.thanh.callnote.MainActivity
import vn.thanh.callnote.Prefs
import vn.thanh.callnote.Scheduler
import vn.thanh.callnote.Status
import vn.thanh.callnote.TranscribeWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Range(val label: String) { TODAY("Hôm nay"), YESTERDAY("Hôm qua"), WEEK("7 ngày"), MONTH("30 ngày") }

private fun Range.bounds(): Pair<Long, Long> = when (this) {
    Range.TODAY -> DayRange.dayStart(0) to DayRange.dayStart(1)
    Range.YESTERDAY -> DayRange.dayStart(-1) to DayRange.dayStart(0)
    Range.WEEK -> DayRange.dayStart(-6) to DayRange.dayStart(1)
    Range.MONTH -> DayRange.dayStart(-29) to DayRange.dayStart(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val db = remember { Db.get(ctx) }
    var range by remember { mutableStateOf(Range.TODAY) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var hasPerm by remember { mutableStateOf(TranscribeWorker.hasAudioPermission(ctx)) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = TranscribeWorker.hasAudioPermission(ctx)
        if (hasPerm) Scheduler.runNow(ctx)
    }
    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(MainActivity.PERMISSIONS) }

    // Tự làm mới danh sách mỗi 3 giây khi đang mở app
    LaunchedEffect(range, tick) {
        while (true) {
            val (from, to) = range.bounds()
            calls = withContext(Dispatchers.IO) { db.between(from, to) }
            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuộc gọi") },
                actions = {
                    IconButton(onClick = { Scheduler.runNow(ctx); tick++; Toast.makeText(ctx, "Đang quét bản ghi mới…", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Refresh, "Xử lý ngay")
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Cài đặt") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!hasPerm) {
                Banner("App cần quyền đọc file ghi âm để hoạt động.", "Cấp quyền") {
                    permLauncher.launch(MainActivity.PERMISSIONS)
                }
            } else if (!prefs.modelReady) {
                Banner("Chưa có mô hình chép lời. Vào Cài đặt để tải (khoảng 190 MB, chỉ tải 1 lần).", "Mở Cài đặt", onSettings)
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Range.entries.forEach { r ->
                    FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label, maxLines = 1, softWrap = false) })
                }
            }

            val done = calls.filter { it.status == Status.DONE }
            val waiting = calls.count { it.status == Status.PENDING || it.status == Status.PROCESSING }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "${calls.size} cuộc · ${done.size} đã chép lời" + if (waiting > 0) " · $waiting chưa chép" else "",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                )
                if (waiting > 0) {
                    OutlinedButton(onClick = {
                        Scheduler.transcribe(ctx)
                        Toast.makeText(ctx, "Bắt đầu chép lời $waiting cuộc…", Toast.LENGTH_SHORT).show()
                    }) { Text("Chép lời") }
                    Spacer(Modifier.width(6.dp))
                }
                Button(enabled = done.isNotEmpty(), onClick = {
                    val text = Export.build(prefs, done, range.label.lowercase())
                    Export.share(ctx, text)
                    db.markSent(done.map { it.id })
                }) {
                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Gửi")
                }
            }

            if (calls.isEmpty()) {
                Text(
                    "Chưa có cuộc gọi nào trong khoảng này.\nApp chỉ xử lý cuộc gọi ghi âm sau mốc thời gian đặt trong Cài đặt.",
                    Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calls, key = { it.id }) { c -> CallCard(c) { onOpen(c.id) } }
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

private val timeFmt = SimpleDateFormat("dd/MM HH:mm", Locale("vi"))

fun statusLabel(s: Int) = when (s) {
    Status.PENDING -> "Chưa chép lời"
    Status.PROCESSING -> "Đang chép lời…"
    Status.DONE -> "Đã chép lời"
    Status.ERROR -> "Lỗi"
    else -> "Bỏ qua"
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
