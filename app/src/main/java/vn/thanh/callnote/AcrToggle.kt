package vn.thanh.callnote

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

/**
 * Bật/tắt dịch vụ trợ năng của Cube ACR Helper.
 * Cần quyền WRITE_SECURE_SETTINGS, cấp 1 lần bằng ADB:
 *   adb shell pm grant vn.thanh.callnote android.permission.WRITE_SECURE_SETTINGS
 */
object AcrToggle {
    const val HELPER_PKG = "com.catalinagroup.callrecorder.helper"
    private const val CUBE_PREFIX = "com.catalinagroup.callrecorder"
    const val ADB_CMD = "adb shell pm grant vn.thanh.callnote android.permission.WRITE_SECURE_SETTINGS"

    fun canWrite(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun listenerOn(ctx: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun enabledList(ctx: Context): List<String> =
        (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').filter { it.isNotBlank() }

    /** Tìm tên dịch vụ trợ năng của Cube ACR (ưu tiên gói Helper). */
    fun service(ctx: Context): ComponentName? {
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(AccessibilityManager::class.java)
        val installed = runCatching { am.installedAccessibilityServiceList }.getOrNull().orEmpty()
            .map { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) }
        val fromEnabled = enabledList(ctx).mapNotNull { ComponentName.unflattenFromString(it) }
        val found = (installed + fromEnabled).let { all ->
            all.firstOrNull { it.packageName == HELPER_PKG } ?: all.firstOrNull { it.packageName.startsWith(CUBE_PREFIX) }
        }
        if (found != null) {
            prefs.acrService = found.flattenToString()
            return found
        }
        return prefs.acrService.takeIf { it.isNotBlank() }?.let { ComponentName.unflattenFromString(it) }
    }

    fun isOn(ctx: Context): Boolean {
        val c = service(ctx) ?: return false
        return enabledList(ctx).any { ComponentName.unflattenFromString(it) == c }
    }

    /** Trả về true nếu đổi được. */
    fun set(ctx: Context, on: Boolean): Boolean {
        if (!canWrite(ctx)) return false
        val c = service(ctx) ?: return false
        val cur = enabledList(ctx).filter { ComponentName.unflattenFromString(it) != c }
        val next = if (on) cur + c.flattenToString() else cur
        return runCatching {
            val cr = ctx.contentResolver
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next.joinToString(":"))
            if (next.isNotEmpty()) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }
}
