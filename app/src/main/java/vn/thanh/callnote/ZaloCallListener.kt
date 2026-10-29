package vn.thanh.callnote

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Nghe thông báo của Zalo. Khi có cuộc gọi Zalo (đến hoặc đi) → bật Cube ACR Helper.
 * Cúp máy → chờ 15 giây cho Cube lưu file → tắt lại, để app ngân hàng/chứng khoán không cảnh báo.
 */
class ZaloCallListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val active = mutableSetOf<String>()
    private val turnOff = Runnable { offIfIdle() }

    override fun onListenerConnected() {
        active.clear()
        runCatching { activeNotifications }.getOrNull()?.forEach { if (isZaloCall(it)) active += it.key }
        if (active.isNotEmpty()) onCall() else offIfIdle()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isZaloCall(sbn)) return
        active += sbn.key
        onCall()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (active.remove(sbn.key) && active.isEmpty()) {
            handler.removeCallbacks(turnOff)
            handler.postDelayed(turnOff, OFF_DELAY_MS)
        }
    }

    private fun onCall() {
        handler.removeCallbacks(turnOff)
        val prefs = Prefs(this)
        if (!prefs.autoAcr || AcrToggle.isOn(this)) return
        if (AcrToggle.set(this, true)) {
            prefs.acrAutoOn = true
            Log.i(TAG, "Zalo call → bật Cube ACR")
        }
    }

    private fun offIfIdle() {
        val prefs = Prefs(this)
        if (active.isNotEmpty() || !prefs.autoAcr || !prefs.acrAutoOn) return
        if (AcrToggle.set(this, false)) {
            prefs.acrAutoOn = false
            Log.i(TAG, "Hết cuộc gọi Zalo → tắt Cube ACR")
        }
    }

    private fun isZaloCall(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != ZALO) return false
        val n = sbn.notification
        val ex = n.extras
        val txt = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT)
            .joinToString(" ") { ex?.getCharSequence(it)?.toString().orEmpty() }.lowercase()
        if ("nhỡ" in txt || "missed" in txt) return false
        if (n.category == Notification.CATEGORY_CALL) return true
        if (ex?.containsKey("android.callType") == true) return true
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val callWords = listOf("cuộc gọi", "đang gọi", "gọi thoại", "gọi video", "đang kết nối", "calling", "call")
        return ongoing && callWords.any { it in txt }
    }

    companion object {
        const val ZALO = "com.zing.zalo"
        private const val OFF_DELAY_MS = 15_000L
        private const val TAG = "ZaloCallListener"
    }
}
