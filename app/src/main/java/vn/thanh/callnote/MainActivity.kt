package vn.thanh.callnote

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import vn.thanh.callnote.ui.DetailScreen
import vn.thanh.callnote.ui.HomeScreen
import vn.thanh.callnote.ui.SettingsScreen
import vn.thanh.callnote.ui.shareDay

sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data class Detail(val id: Long) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notif.setup(this)
        Scheduler.setup(this)
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) darkColorScheme(primary = Color(0xFF7FB2FF))
            else lightColorScheme(primary = Color(0xFF1E5BB8), secondary = Color(0xFFB8321E))
            MaterialTheme(colorScheme = scheme) {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        onOpen = { screen = Screen.Detail(it) },
                        onSettings = { screen = Screen.Settings },
                    )
                    Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                    is Screen.Detail -> DetailScreen(s.id, onBack = { screen = Screen.Home })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Bấm thông báo "Đã tổng hợp…" → mở ngay bảng gửi. */
    private fun handleShare(i: Intent?) {
        val day = i?.getLongExtra(EXTRA_SHARE_DAY, -1L) ?: -1L
        if (day < 0) return
        i?.removeExtra(EXTRA_SHARE_DAY)
        shareDay(this, day)
    }

    companion object {
        const val EXTRA_SHARE_DAY = "share_day"

        val PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
