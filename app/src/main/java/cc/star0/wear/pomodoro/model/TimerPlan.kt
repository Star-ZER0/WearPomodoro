package cc.star0.wear.pomodoro.model

import java.net.URLDecoder
import java.net.URLEncoder

/** A reusable timer configuration. Interface and notification preferences are never included. */
data class TimerPlan(
    val id: Int,
    val focusDurationMillis: Long,
    val shortBreakDurationMillis: Long,
    val longBreakDurationMillis: Long,
    val focusRoundsBeforeLongBreak: Int,
    val name: String = "",
) {
    fun applyTo(settings: PomodoroSettings): PomodoroSettings = settings.copy(
        focusDurationMillis = focusDurationMillis,
        shortBreakDurationMillis = shortBreakDurationMillis,
        longBreakDurationMillis = longBreakDurationMillis,
        focusRoundsBeforeLongBreak = focusRoundsBeforeLongBreak,
    ).sanitized()

    fun matches(settings: PomodoroSettings): Boolean =
        copy(id = 0, name = "") == fromSettings(0, settings)

    fun sanitized(): TimerPlan = fromSettings(id, applyTo(PomodoroSettings())).copy(name = normalizedName(name))

    // Version 2 escapes the optional name so commas, emoji and non-Latin text round-trip.
    fun encode(): String = listOf(
        "v2", id, focusDurationMillis, shortBreakDurationMillis, longBreakDurationMillis,
        focusRoundsBeforeLongBreak, URLEncoder.encode(name, "UTF-8"),
    ).joinToString(",")

    companion object {
        const val MAX_NAME_LENGTH = 40
        val Default = fromSettings(0, PomodoroSettings())

        fun normalizedName(name: String): String {
            val trimmed = name.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
            val length = trimmed.codePointCount(0, trimmed.length)
            return if (length > MAX_NAME_LENGTH) {
                trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_NAME_LENGTH))
            } else {
                trimmed
            }
        }

        fun isValidName(name: String): Boolean = normalizedName(name).isNotEmpty() &&
            name.trim().let { it.codePointCount(0, it.length) <= MAX_NAME_LENGTH }

        fun fromSettings(id: Int, settings: PomodoroSettings): TimerPlan {
            val safe = settings.sanitized()
            return TimerPlan(
                id, safe.focusDurationMillis, safe.shortBreakDurationMillis,
                safe.longBreakDurationMillis, safe.focusRoundsBeforeLongBreak,
            )
        }

        fun decode(encoded: String): TimerPlan? {
            val record = encoded.split(',')
            val version2 = record.firstOrNull() == "v2"
            if (record.size != if (version2) 7 else 5) return null
            val fields = if (version2) record.drop(1) else record
            val name = if (version2) {
                try {
                    URLDecoder.decode(fields[5], "UTF-8")
                } catch (_: IllegalArgumentException) {
                    return null
                }
            } else {
                ""
            }
            val id = fields[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return TimerPlan(
                id = id,
                focusDurationMillis = fields[1].toLongOrNull() ?: return null,
                shortBreakDurationMillis = fields[2].toLongOrNull() ?: return null,
                longBreakDurationMillis = fields[3].toLongOrNull() ?: return null,
                focusRoundsBeforeLongBreak = fields[4].toIntOrNull() ?: return null,
                name = name,
            ).sanitized()
        }

        fun sanitizedPlans(plans: List<TimerPlan>): List<TimerPlan> = plans
            .filter { it.id >= 0 }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .map { it.sanitized() }
    }
}

/** Local editor state: selecting or adding a plan has no effect until confirmation. */
data class TimerPlanDraft(
    val current: TimerPlan,
    val plans: List<TimerPlan>,
    val selectedId: Int?,
    val pendingId: Int? = null,
) {
    val choices: List<TimerPlan> get() = plans
    val selected: TimerPlan? get() = choices.firstOrNull { it.id == selectedId }

    fun saveCurrent(): TimerPlanDraft {
        if (pendingId != null) return this
        val usedIds = plans.map { it.id }.toSet()
        val id = (1..Int.MAX_VALUE).first { it !in usedIds }
        return copy(plans = plans + current.copy(id = id), selectedId = id, pendingId = id)
    }

    fun rename(id: Int, name: String): TimerPlanDraft {
        if (!TimerPlan.isValidName(name)) return this
        return copy(plans = plans.map { if (it.id == id) it.copy(name = TimerPlan.normalizedName(name)) else it })
    }

    fun delete(id: Int): TimerPlanDraft = copy(
        plans = plans.filterNot { it.id == id },
        selectedId = selectedId.takeUnless { it == id },
        pendingId = pendingId.takeUnless { it == id },
    )

    fun encode(): List<String> = listOf(
        current.encode(), selectedId?.toString().orEmpty(), pendingId?.toString().orEmpty(),
    ) + plans.map(TimerPlan::encode)

    companion object {
        fun create(settings: PomodoroSettings, plans: List<TimerPlan>): TimerPlanDraft {
            val sanitizedPlans = TimerPlan.sanitizedPlans(plans)
            return TimerPlanDraft(
                current = TimerPlan.fromSettings(0, settings),
                plans = sanitizedPlans,
                selectedId = sanitizedPlans.firstOrNull { it.matches(settings) }?.id,
            )
        }

        fun decode(values: List<String>): TimerPlanDraft? {
            if (values.size < 3) return null
            val current = TimerPlan.decode(values[0]) ?: return null
            val restoredPlans = values.drop(3).mapNotNull(TimerPlan::decode)
            // Older editor state stored only custom plans, with the classic plan implicit.
            val plans = TimerPlan.sanitizedPlans(
                if (values[0].startsWith("v2,")) restoredPlans else listOf(TimerPlan.Default) + restoredPlans,
            )
            return TimerPlanDraft(
                current = current,
                plans = plans,
                selectedId = values[1].toIntOrNull()?.takeIf { id -> plans.any { it.id == id } },
                pendingId = values[2].toIntOrNull()?.takeIf { id -> plans.any { it.id == id } },
            )
        }
    }
}
