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
 * Mọi bước đều ghi vào AcrLog (xem ở Cài đặt → Nhật ký Cube ACR).
 */
class ZaloCallListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val active = mutableSetOf<String>()
    private val turnOff = Runnable { offIfIdle("hết cuộc gọi") }

    override fun onListenerConnected() {
        AcrLog.add(this, "Đã kết nối quyền đọc thông báo")
        active.clear()
        runCatching { activeNotifications }.getOrNull()?.forEach { if (isZaloCall(it)) active += it.key }
        if (active.isNotEmpty()) onCall() else offIfIdle("khởi động")
    }

    override fun onListenerDisconnected() {
        AcrLog.add(this, "MẤT quyền đọc thông báo (hệ thống ngắt) – sẽ không tự bật được")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != ZALO) return
        val isCall = isZaloCall(sbn)
        AcrLog.add(this, "Thông báo Zalo: ${describe(sbn)} → ${if (isCall) "CUỘC GỌI" else "bỏ qua"}")
        if (!isCall) return
        active += sbn.key
        onCall()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (active.remove(sbn.key) && active.isEmpty()) {
            AcrLog.add(this, "Thông báo cuộc gọi Zalo đã tắt, 15 giây nữa tắt Helper")
            handler.removeCallbacks(turnOff)
            handler.postDelayed(turnOff, OFF_DELAY_MS)
        }
    }

    private fun onCall() {
        handler.removeCallbacks(turnOff)
        val prefs = Prefs(this)
        when {
            !prefs.autoAcr -> AcrLog.add(this, "Không bật: công tắc \"Tự bật khi có cuộc gọi Zalo\" đang TẮT")
            !AcrToggle.canWrite(this) -> AcrLog.add(this, "Không bật: thiếu quyền đặc biệt (lệnh ADB)")
            AcrToggle.service(this) == null -> AcrLog.add(this, "Không bật: không tìm thấy Cube ACR Helper")
            AcrToggle.isOn(this) -> AcrLog.add(this, "Helper đã BẬT sẵn")
            AcrToggle.set(this, true) -> {
                prefs.acrAutoOn = true
                AcrLog.add(this, if (AcrToggle.isOn(this)) "ĐÃ BẬT Helper" else "Đã gửi lệnh bật nhưng Helper vẫn tắt")
                Log.i(TAG, "Zalo call → bật Cube ACR")
            }
            else -> AcrLog.add(this, "Bật Helper THẤT BẠI")
        }
    }

    private fun offIfIdle(reason: String) {
        val prefs = Prefs(this)
        if (active.isNotEmpty() || !prefs.autoAcr) return
        // Tắt cả khi Helper bị bật bằng tay, để cuộc gọi thường không bị Cube ghi.
        if (!AcrToggle.isOn(this)) { prefs.acrAutoOn = false; return }
        if (AcrToggle.set(this, false)) {
            prefs.acrAutoOn = false
            AcrLog.add(this, "ĐÃ TẮT Helper ($reason)")
            Log.i(TAG, "Hết cuộc gọi Zalo → tắt Cube ACR")
        } else AcrLog.add(this, "Tắt Helper THẤT BẠI ($reason)")
    }

    private fun describe(sbn: StatusBarNotification): String {
        val n = sbn.notification
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        return "loại=${n.category ?: "-"}, ${if (ongoing) "đang diễn ra" else "thường"}" +
            (if (n.extras?.containsKey("android.callType") == true) ", callType" else "") +
            ", kênh=${n.channelId ?: "-"}, nội dung=\"${textOf(sbn).take(40)}\""
    }

    private fun textOf(sbn: StatusBarNotification): String {
        val ex = sbn.notification.extras
        return listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT)
            .joinToString(" ") { ex?.getCharSequence(it)?.toString().orEmpty() }.trim()
    }

    private fun isZaloCall(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != ZALO) return false
        val n = sbn.notification
        val txt = textOf(sbn).lowercase()
        if ("nhỡ" in txt || "missed" in txt) return false
        if (n.category == Notification.CATEGORY_CALL) return true
        if (n.extras?.containsKey("android.callType") == true) return true
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val callWords = listOf("cuộc gọi", "đang gọi", "gọi thoại", "gọi video", "đang kết nối", "calling", "call")
        val callChannel = n.channelId?.lowercase()?.contains("call") == true
        return (ongoing || callChannel) && callWords.any { it in txt } || (ongoing && callChannel)
    }

    companion object {
        const val ZALO = "com.zing.zalo"
        private const val OFF_DELAY_MS = 15_000L
        private const val TAG = "ZaloCallListener"
    }
}
