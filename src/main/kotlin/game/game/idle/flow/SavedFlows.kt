package game.idle.flow

import game.idle.IdleState
import game.idle.SavedFlow

/**
 * The player's saved flows: [slots] named flows to switch between, the way into the builder (Maxime, 2026-10-10). The
 * current flow belongs to one slot: the one it was loaded from or saved to, or the empty one it was started in as new,
 * and keeps edits not saved yet. Loading, starting a new flow and emptying the current flow's slot replace the current
 * flow and leave it stopped, so whoever does them stops the autopilot first.
 */
class SavedFlows(val slots: Int) {

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

    /** An empty current flow belonging to the empty [slot], saved there on its first save; throws [FlowError]. */
    fun startNew(state: IdleState, slot: Int): IdleState {
        checkSlot(slot)
        if (state.savedFlows.any { it.slot == slot }) throw FlowError("Saved-flow slot ${slot + 1} holds a flow")
        return state.withFlow(emptyList()).copy(savedSlot = slot)
    }

    /**
     * [slot] emptied; emptying the current flow's slot empties the flow too, so no flow is left that no slot opens
     * (Maxime, 2026-10-10).
     */
    fun empty(state: IdleState, slot: Int): IdleState {
        checkSlot(slot)
        val emptied = state.copy(savedFlows = state.savedFlows.filter { it.slot != slot })
        return if (state.savedSlot == slot) emptied.withFlow(emptyList()).copy(savedSlot = null) else emptied
    }

    /** The saved flow the current flow was last loaded from or saved to, null when none holds it. */
    fun current(state: IdleState): SavedFlow? = state.savedSlot?.let { slot -> state.savedFlows.firstOrNull { it.slot == slot } }

    /** Whether the current flow differs from the saved flow it came from; false when it came from none. */
    fun changedSinceSaved(state: IdleState): Boolean = current(state)?.let { it.steps != state.steps } ?: false

    /**
     * The current flow as the player sees it named: "Willows", "Willows (changed)", "new flow" in a slot it is not saved
     * in yet, or null when it belongs to no slot.
     */
    fun label(state: IdleState): String? {
        val saved = current(state) ?: return state.savedSlot?.let { NEW_FLOW }
        return if (saved.steps != state.steps) "${saved.name} (changed)" else saved.name
    }

    /** The first slot holding no flow, null when every slot holds one. */
    fun firstEmpty(state: IdleState): Int? = (0 until slots).firstOrNull { slot -> state.savedFlows.none { it.slot == slot } }

    private fun checkSlot(slot: Int) {
        if (slot !in 0 until slots) throw FlowError("Saved-flow slot ${slot + 1} is locked")
    }

    companion object {
        const val MAX_NAME = 20

        private const val NEW_FLOW = "new flow"

        /** Printable characters, without `@`: the client reads `@red@` and the like as colour codes. */
        private val NAME_CHARACTERS: Set<Char> = (' '..'~').toSet() - '@'
    }
}
