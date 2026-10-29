package vn.thanh.callnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
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
    var threads by remember { mutableStateOf(prefs.threads.toString()) }
    var folder by remember { mutableStateOf(prefs.folder) }
    var prompt by remember { mutableStateOf(prefs.vocabPrompt) }
    var header by remember { mutableStateOf(prefs.exportHeader) }
    var daily by remember { mutableStateOf(prefs.dailyReminder) }
    var hour by remember { mutableStateOf(prefs.reminderHour.toString()) }
    var importing by remember { mutableStateOf(false) }
    var hq by remember { mutableStateOf(prefs.highQuality) }

    LaunchedEffect(Unit) {
        while (true) {
            progress = withContext(Dispatchers.IO) { ModelManager.poll(ctx, prefs) }
            modelReady = prefs.modelReady
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
        prefs.dailyReminder = daily
        prefs.highQuality = hq
        Scheduler.setup(ctx)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { url = Prefs.SMALL_MODEL_URL }) { Text("Small 190 MB") }
                OutlinedButton(onClick = { url = Prefs.MEDIUM_MODEL_URL }) { Text("Medium 590 MB") }
            }
            Text(
                "Medium chép tiếng Việt đúng hơn rõ rệt nhưng chậm hơn ~3 lần. Chọn rồi bấm Tải mô hình.",
                style = MaterialTheme.typography.bodySmall
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
            Section("Chép lời")
            NumberField(minSec, { minSec = it }, "Bỏ qua cuộc gọi ngắn hơn (giây)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chép lời kỹ")
                    Text(
                        if (hq) "Chính xác hơn, ít bị lặp chữ; chậm hơn ~2–3 lần" else "Nhanh nhưng dễ sai, dễ lặp chữ",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(hq, { hq = it })
            }
            NumberField(threads, { threads = it }, "Số luồng CPU (4 là hợp lý)")
            OutlinedTextField(folder, { folder = it }, label = { Text("Thư mục ghi âm") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                prompt, { prompt = it }, label = { Text("Từ ngữ hay dùng (giúp chép đúng tên riêng)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            HorizontalDivider()
            Section("Tổng hợp trong ngày")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nhắc tổng hợp mỗi tối", Modifier.weight(1f))
                Switch(daily, { daily = it })
            }
            NumberField(hour, { hour = it }, "Giờ nhắc (0–23)")
            OutlinedTextField(
                header, { header = it }, label = { Text("Lời dặn gửi kèm cho Hermes/Claude") },
                modifier = Modifier.fillMaxWidth(), minLines = 3
            )

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
