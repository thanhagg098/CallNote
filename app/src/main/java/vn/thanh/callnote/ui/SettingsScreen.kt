package vn.thanh.callnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.thanh.callnote.AcrToggle
import vn.thanh.callnote.ModelManager
import vn.thanh.callnote.Prefs
import vn.thanh.callnote.Scheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()

    var modelReady by remember { mutableStateOf(prefs.modelReady) }
    var progress by remember { mutableStateOf<ModelManager.Progress?>(null) }
    var url by remember { mutableStateOf(prefs.modelUrl) }
    var minSec by remember { mutableStateOf(prefs.minSeconds.toString()) }
    var days by remember { mutableStateOf("1") }
    var threads by remember { mutableStateOf(prefs.threads.toString()) }
    var folder by remember { mutableStateOf(prefs.folder) }
    var prompt by remember { mutableStateOf(prefs.vocabPrompt) }
    var header by remember { mutableStateOf(prefs.exportHeader) }
    var charging by remember { mutableStateOf(prefs.onlyWhenCharging) }
    var autoTr by remember { mutableStateOf(prefs.autoTranscribe) }
    var daily by remember { mutableStateOf(prefs.dailyReminder) }
    var hour by remember { mutableStateOf(prefs.reminderHour.toString()) }
    var importing by remember { mutableStateOf(false) }
    var zaloTree by remember { mutableStateOf(prefs.zaloTree) }
    var autoAcr by remember { mutableStateOf(prefs.autoAcr) }
    var listenerOn by remember { mutableStateOf(false) }
    var canWrite by remember { mutableStateOf(false) }
    var acrFound by remember { mutableStateOf(false) }
    var acrOn by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs.zaloTree = uri.toString()
            zaloTree = uri.toString()
            Scheduler.runNow(ctx)
            Toast.makeText(ctx, "Đã chọn thư mục ghi âm Zalo", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            progress = withContext(Dispatchers.IO) { ModelManager.poll(ctx, prefs) }
            if (!modelReady && prefs.modelReady) Scheduler.runNow(ctx)
            modelReady = prefs.modelReady
            listenerOn = AcrToggle.listenerOn(ctx)
            canWrite = AcrToggle.canWrite(ctx)
            acrFound = AcrToggle.service(ctx) != null
            acrOn = AcrToggle.isOn(ctx)
            delay(1500)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val r = withContext(Dispatchers.IO) { runCatching { ModelManager.importFrom(ctx, prefs, uri) } }
            importing = false
            Toast.makeText(ctx, r.fold({ "Đã nhập mô hình" }, { "Lỗi: ${it.message}" }), Toast.LENGTH_LONG).show()
        }
    }

    fun save() {
        prefs.modelUrl = url
        minSec.toIntOrNull()?.let { prefs.minSeconds = it }
        threads.toIntOrNull()?.let { prefs.threads = it }
        hour.toIntOrNull()?.let { prefs.reminderHour = it }
        prefs.folder = folder
        prefs.vocabPrompt = prompt
        prefs.exportHeader = header
        prefs.onlyWhenCharging = charging
        prefs.autoTranscribe = autoTr
        prefs.dailyReminder = daily
        Scheduler.schedule(ctx)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Cài đặt") },
            navigationIcon = { IconButton(onClick = { save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Lưu và quay lại") } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Mô hình chép lời (chạy offline)")
            Text(
                if (modelReady) "Đã có: ${prefs.modelName.ifBlank { "model.bin" }} (${prefs.modelFile.length() / 1_000_000} MB)"
                else "Chưa có mô hình.",
                color = if (modelReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            OutlinedTextField(url, { url = it }, label = { Text("Link tải mô hình") }, modifier = Modifier.fillMaxWidth())
            val p = progress
            if (p != null && p.running) {
                val frac = if (p.total > 0) p.done.toFloat() / p.total else 0f
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                Text("Đang tải ${p.done / 1_000_000} / ${p.total.coerceAtLeast(0) / 1_000_000} MB")
            } else if (p != null && p.failed) {
                Text("Tải thất bại. Kiểm tra mạng hoặc link rồi thử lại.", color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { prefs.modelUrl = url; ModelManager.startDownload(ctx, prefs) }) { Text("Tải mô hình") }
                OutlinedButton(enabled = !importing, onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(if (importing) "Đang nhập…" else "Chọn file từ máy")
                }
            }
            Text(
                "Nên tải qua Wi‑Fi. Có thể thay bằng link mô hình PhoWhisper (tiếng Việt tốt hơn) sau khi tạo xong trên GitHub.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()
            Section("Xử lý cuộc gọi")
            NumberField(minSec, { minSec = it }, "Bỏ qua cuộc gọi ngắn hơn (giây)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tự động chép lời")
                    Text(
                        if (autoTr) "Chép lời ngay khi có cuộc mới (tốn pin, nóng máy)"
                        else "Chỉ gom bản ghi. Chép lời khi bấm nút \"Chép lời\"",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(autoTr, { autoTr = it })
            }
            if (autoTr) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chỉ tự chép khi đang sạc", Modifier.weight(1f))
                Switch(charging, { charging = it })
            }
            NumberField(threads, { threads = it }, "Số luồng CPU (4 là hợp lý)")
            OutlinedTextField(folder, { folder = it }, label = { Text("Thư mục ghi âm") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                prompt, { prompt = it }, label = { Text("Từ ngữ hay dùng (giúp chép đúng tên riêng)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(days, { days = it }, "Số ngày", Modifier.weight(1f))
                OutlinedButton(onClick = {
                    val d = days.toIntOrNull()?.coerceIn(0, 60) ?: 1
                    prefs.cutoffTime = System.currentTimeMillis() - d * 24L * 3600_000
                    Scheduler.runNow(ctx)
                    Toast.makeText(ctx, "Sẽ xử lý cuộc gọi trong $d ngày gần nhất", Toast.LENGTH_LONG).show()
                }) { Text("Xử lý lại từ N ngày trước") }
            }

            HorizontalDivider()
            Section("Cuộc gọi Zalo (Cube ACR)")
            Text(
                if (zaloTree.isBlank()) "Chưa chọn. Bấm nút dưới, vào Documents → CubeCallRecorder → \"Sử dụng thư mục này\" → Cho phép."
                else "Đang quét: " + Uri.decode(zaloTree).substringAfterLast(':'),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = {
                val initial = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FCubeCallRecorder")
                treePicker.launch(initial)
            }) { Text(if (zaloTree.isBlank()) "Chọn thư mục Cube ACR" else "Chọn lại thư mục") }

            HorizontalDivider()
            Section("Tự bật Cube ACR khi gọi Zalo")
            Text(
                "Bình thường Cube ACR Helper được tắt để app ngân hàng/chứng khoán không cảnh báo. " +
                    "Khi có cuộc gọi Zalo, app tự bật để ghi âm; cúp máy 15 giây thì tự tắt lại.",
                style = MaterialTheme.typography.bodySmall
            )
            Check("1. Quyền đọc thông báo", listenerOn)
            if (!listenerOn) OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }) { Text("Cấp quyền đọc thông báo") }
            Check("2. Quyền đặc biệt (cấp 1 lần bằng máy tính)", canWrite)
            if (!canWrite) {
                Text(AcrToggle.ADB_CMD, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                OutlinedButton(onClick = {
                    ctx.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("adb", AcrToggle.ADB_CMD))
                    Toast.makeText(ctx, "Đã copy lệnh", Toast.LENGTH_SHORT).show()
                }) { Text("Copy lệnh") }
            }
            Check("3. Tìm thấy Cube ACR Helper", acrFound)
            if (acrFound) Text(
                "Cube ACR hiện đang: " + if (acrOn) "BẬT" else "TẮT",
                color = if (acrOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tự bật khi có cuộc gọi Zalo", Modifier.weight(1f))
                Switch(autoAcr, enabled = listenerOn && canWrite && acrFound, onCheckedChange = {
                    autoAcr = it
                    prefs.autoAcr = it
                    if (it && AcrToggle.set(ctx, false)) {
                        prefs.acrAutoOn = false
                        Toast.makeText(ctx, "Đã tắt Cube ACR. Sẽ tự bật khi có cuộc gọi Zalo.", Toast.LENGTH_LONG).show()
                    }
                })
            }
            if (canWrite && acrFound) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { AcrToggle.set(ctx, true) }) { Text("Thử bật") }
                OutlinedButton(onClick = { AcrToggle.set(ctx, false); prefs.acrAutoOn = false }) { Text("Thử tắt") }
            }

            HorizontalDivider()
            Section("Gửi tổng hợp")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nhắc gửi tổng hợp hằng ngày", Modifier.weight(1f))
                Switch(daily, { daily = it })
            }
            NumberField(hour, { hour = it }, "Giờ nhắc (0–23)")
            OutlinedTextField(
                header, { header = it }, label = { Text("Lời dặn gửi kèm cho Hermes/Claude") },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )

            HorizontalDivider()
            Section("Chạy nền ổn định")
            Text("Samsung hay tắt app chạy nền. Bấm nút dưới và chọn \"Cho phép\" để app không bị tắt.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                val pm = ctx.getSystemService(PowerManager::class.java)
                if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                    Toast.makeText(ctx, "Đã được phép chạy nền", Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                }
            }) { Text("Cho phép chạy nền") }

            Button(onClick = { save(); Toast.makeText(ctx, "Đã lưu", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                Text("Lưu cài đặt")
            }
        }
    }
}

@Composable
private fun Check(t: String, ok: Boolean) = Text(
    (if (ok) "✓ " else "✗ ") + t,
    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
)

@Composable
private fun Section(t: String) = Text(t, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)

@Composable
private fun NumberField(v: String, on: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) =
    OutlinedTextField(
        v, { s -> on(s.filter { it.isDigit() }.take(4)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier
    )
