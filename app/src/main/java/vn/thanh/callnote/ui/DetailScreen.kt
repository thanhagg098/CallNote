package vn.thanh.callnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import vn.thanh.callnote.CallRecord
import vn.thanh.callnote.Db
import vn.thanh.callnote.Export
import vn.thanh.callnote.Prefs
import vn.thanh.callnote.Scheduler
import vn.thanh.callnote.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var rec by remember { mutableStateOf<CallRecord?>(null) }
    LaunchedEffect(id) {
        while (true) {
            rec = withContext(Dispatchers.IO) { db.byId(id) }
            delay(3000)
        }
    }
    val c = rec
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(c?.contact ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } }
        )
    }) { pad ->
        if (c == null) return@Scaffold
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", Locale("vi")).format(Date(c.callTime)) +
                    " · " + Export.duration(c.durationMs) + " · " + statusLabel(c.status),
                style = MaterialTheme.typography.bodySmall
            )
            if (c.error.isNotBlank()) Text(c.error, color = MaterialTheme.colorScheme.error)

            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(c.uri), "audio/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { ctx.startActivity(i) }
                }) { Icon(Icons.Default.PlayArrow, null); Text("Nghe") }
                OutlinedButton(enabled = c.transcript.isNotBlank(), onClick = {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("transcript", c.transcript))
                    Toast.makeText(ctx, "Đã chép", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentCopy, null); Text("Chép") }
                OutlinedButton(enabled = c.status == Status.DONE, onClick = {
                    Export.share(ctx, Export.build(Prefs(ctx), listOf(c), "lẻ"))
                    db.markSent(listOf(c.id))
                }) { Icon(Icons.Default.Share, null); Text("Gửi") }
            }
            if (c.status != Status.PROCESSING) {
                OutlinedButton(onClick = {
                    db.setStatus(c.id, Status.PENDING, error = "")
                    Scheduler.transcribe(ctx, c.id)
                    Toast.makeText(ctx, "Đang chép lời cuộc này…", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.Replay, null); Text(if (c.status == Status.DONE) "Chép lời lại" else "Chép lời") }
            }
            SelectionContainer {
                Text(c.transcript.ifBlank { "(chưa có nội dung)" }, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
