package cc.star0.wear.pomodoro

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.star0.wear.pomodoro.permissions.AppPermissionReport
import cc.star0.wear.pomodoro.permissions.PermissionAction
import cc.star0.wear.pomodoro.permissions.readAppPermissions
import cc.star0.wear.pomodoro.timer.PomodoroService
import cc.star0.wear.pomodoro.ui.PomodoroApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var permissionReport by mutableStateOf(AppPermissionReport())
    private var permissionChecksStarted = false
    private var permissionRefreshJob: Job? = null
    private var openTimerRequested by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (permissionChecksStarted) refreshPermissions()
            PomodoroService.refreshNotifications(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTimerRequested = savedInstanceState?.getBoolean(STATE_OPEN_TIMER_REQUESTED)
            ?: (intent?.action == ACTION_OPEN_TIMER)
        setContent {
            PomodoroApp(
                viewModel = viewModel(),
                permissionReport = permissionReport,
                onPermissionAction = ::handlePermissionAction,
                onRefreshPermissions = ::refreshPermissions,
                onOpenUrl = ::openExternalUrl,
                openTimerRequested = openTimerRequested,
                onTimerOpened = { openTimerRequested = false },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // The timer page does not need a full permission report during cold startup.
        if (permissionChecksStarted) refreshPermissions()
        PomodoroService.refreshNotifications(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_TIMER) openTimerRequested = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_OPEN_TIMER_REQUESTED, openTimerRequested)
        super.onSaveInstanceState(outState)
    }

    private fun refreshPermissions() {
        permissionChecksStarted = true
        permissionRefreshJob?.cancel()
        permissionRefreshJob = lifecycleScope.launch {
            permissionReport = withContext(Dispatchers.IO) {
                readAppPermissions(applicationContext)
            }
        }
    }

    private fun handlePermissionAction(action: PermissionAction) {
        when (action) {
            PermissionAction.Notifications -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    openSettings(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                    )
                } else {
                    openAppSettings()
                }
            }
            PermissionAction.ExactAlarms -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
                }
            }
            PermissionAction.LiveUpdates -> {
                if (Build.VERSION.SDK_INT >= 36) {
                    openSettings(
                        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                    )
                }
            }
            PermissionAction.AppSettings -> openAppSettings()
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppSettings()
        } catch (_: SecurityException) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.open_app_settings_error, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.open_app_settings_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_link_handler_error, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.open_link_error, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_OPEN_TIMER = "cc.star0.wear.pomodoro.action.OPEN_TIMER"
        private const val STATE_OPEN_TIMER_REQUESTED = "open_timer_requested"
    }
}
