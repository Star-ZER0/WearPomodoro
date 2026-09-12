package cc.star0.wear.pomodoro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.timer.PomodoroService

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = (application as PomodoroApplication).pomodoroController

    val settings = controller.settings
    val state = controller.state
    val isInitialized = controller.isInitialized

    fun startFocus() {
        PomodoroService.start(getApplication())
    }

    fun pause() {
        PomodoroService.send(getApplication(), PomodoroService.ACTION_PAUSE)
    }

    fun resume() {
        PomodoroService.send(getApplication(), PomodoroService.ACTION_RESUME)
    }

    fun stop() {
        PomodoroService.send(getApplication(), PomodoroService.ACTION_STOP_CONFIRMED)
    }

    fun updateSettings(settings: PomodoroSettings) {
        controller.updateSettings(settings)
    }
}
