package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource

/**
 * Where a processing step takes what it works on (Maxime, S01), kept under [KEY]: what the steps before it get, or the
 * bank. A new step follows the flow (Maxime, 2026-10-10): earlier steps when one of them gets something it can use,
 * else the bank; a step saved without the setting reads the same rule.
 */
object ProcessInput {

    const val KEY = "input"

    const val EARLIER = "earlier"

    const val BANK = "bank"

    /** The choice kept in [settings], else the flow's rule for a step that can use [usable]. */
    fun read(settings: StepSettings, before: FlowContext, usable: Set<Int>): InputSource =
        when (settings[KEY]) {
            EARLIER -> InputSource.EARLIER_STEPS
            BANK -> InputSource.BANK
            else -> rule(before, usable)
        }

    /** [settings] with the flow's rule kept, for a new step that can use [usable]. */
    fun initial(settings: StepSettings, before: FlowContext, usable: Set<Int>): StepSettings =
        settings.with(KEY, word(rule(before, usable)))

    /**
     * On earlier steps, throws [FlowError] naming the first of [needed] (item ids) no step before gets, with [names];
     * the bank is never checked here (S07b warns when no bank step withdraws it).
     */
    fun requireGathered(input: InputSource, before: FlowContext, needed: List<Int>, names: GameNames) {
        if (input == InputSource.BANK) return
        val missing = needed.firstOrNull { it !in before.gathered } ?: return
        throw FlowError("no step before gets ${names.item(missing).lowercase()}")
    }

    fun word(input: InputSource): String = if (input == InputSource.BANK) BANK else EARLIER

    /** The configure screen's Input toggle. */
    fun field(usable: Set<Int>): StepField.Toggle =
        StepField.Toggle(
            KEY,
            "Input",
            listOf(Choice(EARLIER, "Earlier steps"), Choice(BANK, "The bank")),
            current = { settings, before -> word(read(settings, before, usable)) },
        )

    private fun rule(before: FlowContext, usable: Set<Int>): InputSource =
        if (before.gathered.any { it in usable }) InputSource.EARLIER_STEPS else InputSource.BANK
}
