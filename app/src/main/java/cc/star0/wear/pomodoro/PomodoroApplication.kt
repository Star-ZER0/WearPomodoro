package cc.star0.wear.pomodoro

import android.app.Application
import android.content.res.Configuration
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat
import cc.star0.wear.lib.miwearhaptics.WearHapticFeedbackConstantsCompat.Policy
import cc.star0.wear.pomodoro.notifications.PomodoroNotifications
import cc.star0.wear.pomodoro.timer.PomodoroController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PomodoroApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val pomodoroController: PomodoroController by lazy {
        PomodoroController(this, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        // Choose the SDK policy before Compose caches rotary feedback constants.
        WearHapticFeedbackConstantsCompat.setPolicy(Policy.GOOGLE_FIRST)
        PomodoroNotifications.createChannels(this)
        pomodoroController
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Channel labels are stored by the system, so update them when the app locale changes.
        PomodoroNotifications.createChannels(this)
    }
}
