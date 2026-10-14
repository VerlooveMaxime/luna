package game.idle.flow

/** When a whole flow stops (S12, Maxime's S01 decision): never, after a number of laps or minutes, or once every goal is met. */
sealed interface FlowStop {

    data object Never : FlowStop

    data class AfterLaps(val laps: Int) : FlowStop

    data class AfterMinutes(val minutes: Int) : FlowStop

    data object AllGoalsMet : FlowStop
}

/**
 * The flow stop as the player set it on the overview, saved with the flow: its [kind] ([FlowStops.LAPS], [FlowStops.TIME],
 * [FlowStops.GOALS], anything else never) and the number typed [after] it, as text so a save stays plain.
 */
data class StopSettings(val kind: String = FlowStops.NEVER, val after: String = "")

/** Reads a [StopSettings] into the [FlowStop] it means; the overview's row and its rules. */
object FlowStops {

    const val NEVER = "never"
    const val LAPS = "laps"
    const val TIME = "time"
    const val GOALS = "goals"

    /** The overview's choices, in its order. */
    val CHOICES = listOf(Choice(NEVER, "Never"), Choice(LAPS, "After laps"), Choice(TIME, "After time"), Choice(GOALS, "When all goals are met"))

    private const val ONE_OR_MORE = "1 or more"
    val LAPS_RULE = Reasons.takes("Laps", ONE_OR_MORE)
    val MINUTES_RULE = Reasons.takes("Minutes", ONE_OR_MORE)

    /** Throws [FlowError] with the rule a typed number broke, or for a kind no stop has. */
    fun read(settings: StopSettings): FlowStop =
        when (settings.kind) {
            NEVER -> FlowStop.Never
            LAPS -> FlowStop.AfterLaps(number(settings.after, "Laps"))
            TIME -> FlowStop.AfterMinutes(number(settings.after, "Minutes"))
            GOALS -> FlowStop.AllGoalsMet
            else -> throw FlowError("'${settings.kind}' is not a kind of stop")
        }

    /** Whether the stop takes a number: after laps, after time. */
    fun typed(kind: String): Boolean = kind == LAPS || kind == TIME

    /** The box's words after the row's choice: "5 laps", "30 min", none while nothing is typed. */
    fun afterWords(settings: StopSettings): String? {
        if (!typed(settings.kind) || settings.after.isEmpty()) return null
        return "${StepAmount.short(settings.after)} ${if (settings.kind == LAPS) "laps" else "min"}"
    }

    private fun number(text: String, label: String): Int {
        if (text.isEmpty()) throw FlowError(Reasons.needs(label, countable = false))
        val value = text.toIntOrNull()
        if (value == null || value < 1) throw FlowError(Reasons.takes(label, ONE_OR_MORE, text))
        return value
    }
}
