package cc.star0.wear.pomodoro.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cc.star0.wear.pomodoro.PomodoroApplication
import kotlinx.coroutines.launch

/** Elapsed clocks reset on reboot, so the timer returns to a safe ready state. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val application = context.applicationContext as? PomodoroApplication ?: return
        val pendingResult = goAsync()
        application.applicationScope.launch {
            try {
                application.pomodoroController.resetAfterReboot()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
