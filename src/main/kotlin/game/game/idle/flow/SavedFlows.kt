package game.idle.flow

import game.idle.IdleState
import game.idle.SavedFlow

/**
 * The player's saved flows: [slots] named copies of a flow to switch between. Loading replaces the current flow and
 * leaves it stopped, so whoever loads a flow stops the autopilot first.
 */
class SavedFlows(private val slots: Int) {

    init {
        require(slots > 0) { "There must be at least one saved-flow slot" }
    }

    /** Copies the current flow into [slot] under [name], replacing what the slot held; throws [FlowError]. */
    fun save(state: IdleState, slot: Int, name: String): IdleState {
        checkSlot(slot)
        val trimmed = name.trim()
        if (trimmed.length !in 1..MAX_NAME) throw FlowError("A flow's name is 1 to $MAX_NAME characters")
        if (trimmed.any { it !in NAME_CHARACTERS }) throw FlowError("A flow's name is letters, digits, spaces and punctuation")
        if (state.steps.isEmpty()) throw FlowError("The flow is empty: add a step before saving it")
        val saved = SavedFlow(slot, trimmed, state.steps)
        return state.copy(savedFlows = (state.savedFlows.filter { it.slot != slot } + saved).sortedBy { it.slot }, savedSlot = slot)
    }

    /** The flow in [slot] as the current one, from its first step; throws [FlowError] when the slot is empty. */
    fun load(state: IdleState, slot: Int): IdleState {
        checkSlot(slot)
        val saved = state.savedFlows.firstOrNull { it.slot == slot } ?: throw FlowError("Saved-flow slot ${slot + 1} is empty")
        return state.withFlow(saved.steps).copy(savedSlot = slot)
    }

    /** [slot] emptied; the current flow keeps its steps but no longer counts as saved there. */
    fun empty(state: IdleState, slot: Int): IdleState {
        checkSlot(slot)
        return state.copy(
            savedFlows = state.savedFlows.filter { it.slot != slot },
            savedSlot = state.savedSlot.takeIf { it != slot },
        )
    }

    /** Whether the current flow differs from the saved flow it came from; false when it came from none. */
    fun changedSinceSaved(state: IdleState): Boolean {
        val slot = state.savedSlot ?: return false
        val saved = state.savedFlows.firstOrNull { it.slot == slot } ?: return false
        return saved.steps != state.steps
    }

    private fun checkSlot(slot: Int) {
        if (slot !in 0 until slots) throw FlowError("Saved-flow slot ${slot + 1} is locked")
    }

    companion object {
        const val MAX_NAME = 20

        /** Printable characters, without `@`: the client reads `@red@` and the like as colour codes. */
        private val NAME_CHARACTERS: Set<Char> = (' '..'~').toSet() - '@'
    }
}
