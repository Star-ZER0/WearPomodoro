package cc.star0.wear.pomodoro.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.star0.wear.pomodoro.R

enum class PermissionStatus { Granted, Missing, NotRequired }
enum class PermissionAction { Notifications, ExactAlarms, AppSettings }

data class AppPermission(
    val name: String,
    val title: String,
    val description: String,
    val status: PermissionStatus,
    val action: PermissionAction,
)

data class AppPermissionReport(
    val permissions: List<AppPermission> = emptyList(),
    val versionName: String = "",
    val isLoaded: Boolean = false,
) {
    val missingCount: Int get() = permissions.count { it.status == PermissionStatus.Missing }
    val grantedCount: Int get() = permissions.count { it.status == PermissionStatus.Granted }
}

internal fun permissionStatus(
    name: String,
    sdk: Int,
    granted: Boolean,
    notificationsEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
): PermissionStatus {
    val allowed = when (name) {
        Manifest.permission.POST_NOTIFICATIONS -> notificationsEnabled && (sdk < 33 || granted)
        Manifest.permission.SCHEDULE_EXACT_ALARM -> {
            if (sdk < 31) return PermissionStatus.NotRequired
            exactAlarmsAllowed
        }
        Manifest.permission.FOREGROUND_SERVICE -> {
            if (sdk < 28) return PermissionStatus.NotRequired
            granted
        }
        Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE -> {
            if (sdk < 34) return PermissionStatus.NotRequired
            granted
        }
        else -> granted
    }
    return if (allowed) PermissionStatus.Granted else PermissionStatus.Missing
}

/** Reads all permissions declared by this APK, including special-access permissions. */
fun readAppPermissions(context: Context): AppPermissionReport {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    val permissions = packageInfo.requestedPermissions.orEmpty().mapIndexed { index, name ->
        // GET_PERMISSIONS already includes grant flags, avoiding another system call per entry.
        val granted = packageInfo.requestedPermissionsFlags?.getOrNull(index)?.let { flags ->
            flags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
        } ?: (ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED)
        val textResources = when (name) {
            Manifest.permission.POST_NOTIFICATIONS -> R.string.permission_notifications_title to R.string.permission_notifications_description
            Manifest.permission.SCHEDULE_EXACT_ALARM -> R.string.permission_exact_alarms_title to R.string.permission_exact_alarms_description
            Manifest.permission.FOREGROUND_SERVICE -> R.string.permission_foreground_service_title to R.string.permission_foreground_service_description
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE -> R.string.permission_timer_service_title to R.string.permission_timer_service_description
            Manifest.permission.RECEIVE_BOOT_COMPLETED -> R.string.permission_boot_title to R.string.permission_boot_description
            Manifest.permission.VIBRATE -> R.string.permission_vibrate_title to R.string.permission_vibrate_description
            Manifest.permission.WAKE_LOCK -> R.string.permission_wake_lock_title to R.string.permission_wake_lock_description
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" -> R.string.permission_internal_receiver_title to R.string.permission_internal_receiver_description
            else -> null
        }
        val title = if (textResources != null) {
            context.getString(textResources.first)
        } else {
            try {
                context.packageManager.getPermissionInfo(name, 0).loadLabel(context.packageManager).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                name.substringAfterLast('.')
            }
        }
        AppPermission(
            name = name,
            title = title,
            description = context.getString(textResources?.second ?: R.string.permission_system_description),
            status = permissionStatus(
                name = name,
                sdk = Build.VERSION.SDK_INT,
                granted = granted,
                notificationsEnabled = notificationsEnabled,
                exactAlarmsAllowed = exactAlarmsAllowed,
            ),
            action = when (name) {
                Manifest.permission.POST_NOTIFICATIONS -> PermissionAction.Notifications
                Manifest.permission.SCHEDULE_EXACT_ALARM -> PermissionAction.ExactAlarms
                else -> PermissionAction.AppSettings
            },
        )
    }
    return AppPermissionReport(permissions, packageInfo.versionName.orEmpty(), isLoaded = true)
}
