package game.idle.flow

import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotPlayer

/**
 * `::flow add <step>`, `list`, `clear`, `run`, `resume`, `stop`, and the `::idle <tree>` shorthand that replaces the
 * flow with chopping that tree around the player and dropping the logs, and runs it.
 */
class FlowCommand<P : AutopilotPlayer>(private val autopilot: Autopilot<P>, private val resolver: FlowResolver) {

    fun flow(player: P, args: List<String>) {
        val verb = args.firstOrNull()
        val rest = args.drop(1)
        when (verb) {
            "add" -> add(player, rest.joinToString(" "))
            "list" -> list(player)
            "clear" -> clear(player)
            "run" -> run(player, fromStart = true)
            "resume" -> run(player, fromStart = false)
            "stop" -> stop(player)
            else -> player.tell("::flow add <step> | list | clear | run | resume | stop. Steps: ${resolver.grammar.help}")
        }
    }

    /** Without a tree, stops a running flow or says how to use it. */
    fun idle(player: P, args: List<String>) {
        val tree = args.firstOrNull()
        if (tree == null) {
            return if (autopilot.isRunning(player)) stop(player) else player.tell("Autopilot: ::idle <tree> chops it around you.")
        }
        val lines = listOf("chop $tree", "drop")
        try {
            resolver.resolve(lines)
        } catch (e: FlowError) {
            return player.tell("Autopilot: ${e.message}")
        }
        player.idleState = player.idleState.withFlow(lines)
        run(player, fromStart = true)
    }

    private fun add(player: P, line: String) {
        if (autopilot.isRunning(player)) return player.tell("Autopilot: stop the flow first (::flow stop).")
        val lines = player.idleState.flow + line
        try {
            resolver.resolve(lines)
        } catch (e: FlowError) {
            return player.tell("Autopilot: ${e.message}")
        }
        player.idleState = player.idleState.withFlow(lines)
        player.tell("Autopilot: step ${lines.size}: $line")
    }

    private fun list(player: P) {
        val state = player.idleState
        if (state.flow.isEmpty()) return player.tell("Autopilot: the flow is empty. ::flow add <step>")
        val status = if (state.running) "running" else "stopped"
        player.tell("Autopilot: flow ($status, at step ${state.stepIndex + 1}):")
        state.flow.forEachIndexed { index, line -> player.tell("${index + 1}. $line") }
    }

    private fun clear(player: P) {
        autopilot.stop(player)
        player.idleState = player.idleState.withFlow(emptyList())
        player.tell("Autopilot: flow cleared.")
    }

    private fun run(player: P, fromStart: Boolean) {
        val state = player.idleState
        if (state.flow.isEmpty()) return player.tell("Autopilot: the flow is empty. ::flow add <step>")
        try {
            resolver.resolve(state.flow)
        } catch (e: FlowError) {
            return player.tell("Autopilot: ${e.message}")
        }
        player.idleState = if (fromStart) state.fromStart() else state
        autopilot.start(player)
        player.tell("Autopilot: running step ${player.idleState.stepIndex + 1}: ${state.flow[player.idleState.stepIndex]}")
    }

    private fun stop(player: P) {
        autopilot.stop(player)
        player.tell("Autopilot: stopped.")
    }
}
