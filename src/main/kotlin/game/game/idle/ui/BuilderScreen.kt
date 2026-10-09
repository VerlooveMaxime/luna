package game.idle.ui

import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotPlayer
import game.idle.flow.FlowResolver

/** What the game shows after a click on the builder's screens. */
sealed interface BuilderAnswer {

    data object Ignored : BuilderAnswer

    data object Close : BuilderAnswer

    /** Shows [page] (the current one when null) with the state as it now is, and says [message] in the chat box. */
    data class Show(val page: BuilderPage? = null, val message: String = "") : BuilderAnswer
}

/**
 * The clicks and drags of the builder's overview and kind picker (flow builder v2, S06a), over the flow saved in the
 * player's state and the [Autopilot] that runs it; a flow has room for [slots] steps. While the flow runs nothing is
 * changed (Maxime, 2026-10-09); messages go to the chat box. The configure screen joins in S06b: until then a slot or
 * a kind picked only says so.
 */
class BuilderScreen<P : AutopilotPlayer>(
    private val autopilot: Autopilot<P>,
    private val resolver: FlowResolver,
    private val slots: Int,
) {

    private val types = resolver.types.all

    fun click(player: P, widgetId: Int): BuilderAnswer =
        when (widgetId) {
            BuilderWidgets.CLOSE -> BuilderAnswer.Close
            BuilderWidgets.RUN -> run(player)
            BuilderWidgets.STOP -> stop(player)
            BuilderWidgets.CLEAR -> clear(player)
            BuilderWidgets.BASE_LEVELS -> levels(player, boosted = false)
            BuilderWidgets.BOOSTED_LEVELS -> levels(player, boosted = true)
            BuilderWidgets.KINDS_BACK -> BuilderAnswer.Show(BuilderPage.OVERVIEW)
            else -> BuilderWidgets.slotOf(widgetId)?.let { slot(player, it) }
                ?: BuilderWidgets.kindOf(widgetId)?.let { kind(player, it) }
                ?: BuilderAnswer.Ignored
        }

    /** A slot dragged onto another: it moves there, the steps in between shifting by one; dropped past them it goes last. */
    fun arrange(player: P, from: Int, to: Int): BuilderAnswer {
        val steps = player.idleState.steps
        if (from !in steps.indices) return BuilderAnswer.Ignored
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(message = STOP_FIRST)
        val moved = steps.toMutableList().apply { add(to.coerceAtMost(steps.lastIndex).coerceAtLeast(0), removeAt(from)) }
        player.idleState = player.idleState.withFlow(moved)
        return BuilderAnswer.Show()
    }

    private fun run(player: P): BuilderAnswer {
        val steps = player.idleState.steps
        if (steps.isEmpty()) return say("the flow is empty. Add a step first.")
        val cannot = resolver.problems(steps).indexOfFirst { it != null }
        if (cannot >= 0) return BuilderAnswer.Show(message = Autopilot.cannotWork(cannot + 1))
        player.idleState = player.idleState.fromStart()
        autopilot.start(player)
        return say("running from step 1.")
    }

    private fun stop(player: P): BuilderAnswer {
        autopilot.stop(player)
        return say("stopped.")
    }

    private fun clear(player: P): BuilderAnswer {
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(message = STOP_FIRST)
        if (player.idleState.steps.isEmpty()) return BuilderAnswer.Show()
        player.idleState = player.idleState.withFlow(emptyList())
        return say("flow cleared.")
    }

    private fun levels(player: P, boosted: Boolean): BuilderAnswer {
        player.idleState = player.idleState.copy(countBoostedLevels = boosted)
        return BuilderAnswer.Show()
    }

    /** A step's slot opens its configure screen (S06b); the first free slot opens the kind picker. */
    private fun slot(player: P, slot: Int): BuilderAnswer {
        val steps = player.idleState.steps
        steps.getOrNull(slot)?.let { return say("step ${slot + 1} (${it.kind}) opens its configure screen in S06b.") }
        if (slot != steps.size || slot >= slots) return BuilderAnswer.Ignored
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(message = STOP_FIRST)
        return BuilderAnswer.Show(BuilderPage.KINDS)
    }

    private fun kind(player: P, kind: Int): BuilderAnswer {
        val type = types.getOrNull(kind) ?: return BuilderAnswer.Ignored
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(BuilderPage.OVERVIEW, STOP_FIRST)
        return BuilderAnswer.Show(BuilderPage.OVERVIEW, "$PREFIX a new ${type.kind} step: its configure screen comes in S06b.")
    }

    private fun say(message: String): BuilderAnswer = BuilderAnswer.Show(message = "$PREFIX $message")

    companion object {
        const val PREFIX = "Autopilot:"
        const val STOP_FIRST = "$PREFIX stop the flow before editing it."
    }
}
