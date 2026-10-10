package game.idle.flow

import game.idle.IdleState
import game.idle.SavedFlow
import game.idle.flow.FakeStepType.Companion.step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SavedFlowsTest {

    private val saved = SavedFlows(slots = 2)
    private val chopFlow = listOf(step("chop", "oak"), step("drop"))
    private val fishFlow = listOf(step("fish"))
    private val current = IdleState(steps = chopFlow)
    private val eat = listOf(ReflexSettings(1, mapOf("below" to "40")))

    @Test
    fun `saving copies the current flow into the slot under its name`() {
        val state = saved.save(current, slot = 1, name = "Oaks")

        assertEquals(listOf(SavedFlow(1, "Oaks", chopFlow)), state.savedFlows)
    }

    @Test
    fun `the saved flow becomes the one the current flow counts as saved in`() {
        assertEquals(1, saved.save(current, slot = 1, name = "Oaks").savedSlot)
    }

    @Test
    fun `saving over a slot replaces what it held, the slots kept in order`() {
        val before = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow), SavedFlow(1, "Old", fishFlow)))

        val state = saved.save(before, slot = 1, name = "New")

        assertEquals(listOf(SavedFlow(0, "Fish", fishFlow), SavedFlow(1, "New", chopFlow)), state.savedFlows)
    }

    @Test
    fun `a name is trimmed`() {
        assertEquals("Oaks", saved.save(current, slot = 0, name = "  Oaks ").savedFlows.single().name)
    }

    @Test
    fun `a name is 1 to 20 characters`() {
        assertRejected("A flow's name is 1 to 20 characters") { saved.save(current, slot = 0, name = "   ") }
        assertRejected("A flow's name is 1 to 20 characters") { saved.save(current, slot = 0, name = "a".repeat(21)) }
    }

    @Test
    fun `a name with a colour code or an unprintable character is refused`() {
        assertRejected("A flow's name is letters, digits, spaces and punctuation") { saved.save(current, slot = 0, name = "@red@Oaks") }
        assertRejected("A flow's name is letters, digits, spaces and punctuation") { saved.save(current, slot = 0, name = "Oa\tks") }
    }

    @Test
    fun `an empty flow cannot be saved`() {
        assertRejected("The flow is empty: add a step before saving it") { saved.save(IdleState(), slot = 0, name = "Nothing") }
    }

    @Test
    fun `a slot beyond the unlocked ones is locked`() {
        assertRejected("Saved-flow slot 3 is locked") { saved.save(current, slot = 2, name = "Oaks") }
        assertRejected("Saved-flow slot 0 is locked") { saved.load(current, slot = -1) }
        assertRejected("Saved-flow slot 3 is locked") { saved.empty(current, slot = 2) }
    }

    @Test
    fun `loading makes the saved flow the current one, stopped and from its first step`() {
        val before = IdleState(steps = chopFlow, stepIndex = 1, running = true, laps = 3, savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)))

        val state = saved.load(before, slot = 0)

        assertEquals(IdleState(steps = fishFlow, savedFlows = before.savedFlows, savedSlot = 0), state)
    }

    @Test
    fun `loading takes the flow of that slot`() {
        val before = IdleState(savedFlows = listOf(SavedFlow(0, "Oaks", chopFlow), SavedFlow(1, "Fish", fishFlow)))

        assertEquals(fishFlow, saved.load(before, slot = 1).steps)
    }

    @Test
    fun `an empty slot cannot be loaded`() {
        assertRejected("Saved-flow slot 2 is empty") { saved.load(current, slot = 1) }
    }

    @Test
    fun `emptying a slot removes its flow`() {
        val before = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow), SavedFlow(1, "Oaks", chopFlow)))

        assertEquals(listOf(SavedFlow(1, "Oaks", chopFlow)), saved.empty(before, slot = 0).savedFlows)
    }

    @Test
    fun `emptying the slot the current flow belongs to empties the flow too`() {
        val before = current.copy(savedFlows = listOf(SavedFlow(1, "Oaks", chopFlow)), savedSlot = 1, laps = 3)

        assertEquals(IdleState(), saved.empty(before, slot = 1))
    }

    @Test
    fun `emptying another slot keeps the flow and the slot it belongs to`() {
        val before = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)), savedSlot = 1)

        assertEquals(listOf<Any?>(chopFlow, 1), saved.empty(before, slot = 0).let { listOf(it.steps, it.savedSlot) })
    }

    @Test
    fun `emptying a slot of a flow belonging to none keeps the flow`() {
        val before = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)))

        assertEquals(chopFlow, saved.empty(before, slot = 0).steps)
    }

    @Test
    fun `a new flow is empty and belongs to its slot`() {
        val before = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)), savedSlot = 0, laps = 2)

        assertEquals(IdleState(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)), savedSlot = 1), saved.startNew(before, slot = 1))
    }

    @Test
    fun `a new flow needs an empty slot`() {
        val before = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)))

        assertThrows<FlowError> { saved.startNew(before, slot = 0) }
    }

    @Test
    fun `a new flow cannot start in a locked slot`() {
        assertThrows<FlowError> { saved.startNew(current, slot = 2) }
    }

    @Test
    fun `a new flow not saved yet is a new flow`() {
        assertEquals("new flow", saved.label(saved.startNew(current, slot = 1)))
    }

    @Test
    fun `a flow edited since it was saved has changed`() {
        val state = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)), savedSlot = 0)

        assertTrue(saved.changedSinceSaved(state))
    }

    @Test
    fun `a flow as it was saved has not changed`() {
        assertFalse(saved.changedSinceSaved(saved.save(current, slot = 0, name = "Oaks")))
    }

    @Test
    fun `a flow saved nowhere has not changed`() {
        assertFalse(saved.changedSinceSaved(current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)))))
    }

    @Test
    fun `a flow is compared with the slot it came from, not the others`() {
        val state = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow), SavedFlow(1, "Oaks", chopFlow)), savedSlot = 1)

        assertFalse(saved.changedSinceSaved(state))
    }

    @Test
    fun `a flow linked to a slot that holds nothing has not changed`() {
        assertFalse(saved.changedSinceSaved(current.copy(savedSlot = 1)))
    }

    @Test
    fun `the current saved flow is the one in the slot the flow came from`() {
        val state = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow), SavedFlow(1, "Oaks", fishFlow)), savedSlot = 1)

        assertEquals("Oaks", saved.current(state)?.name)
    }

    @Test
    fun `a flow saved nowhere has no current saved flow`() {
        assertNull(saved.current(current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)))))
    }

    @Test
    fun `a flow as it was saved goes by its name`() {
        assertEquals("Oaks", saved.label(saved.save(current, slot = 0, name = "Oaks")))
    }

    @Test
    fun `a flow edited since it was saved goes by its name and changed`() {
        val state = saved.save(current, slot = 0, name = "Oaks").copy(steps = fishFlow)

        assertEquals("Oaks (changed)", saved.label(state))
    }

    @Test
    fun `a flow saved nowhere has no label`() {
        assertNull(saved.label(current))
    }

    @Test
    fun `the first empty slot is the lowest holding nothing`() {
        assertEquals(1, saved.firstEmpty(current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow)))))
    }

    @Test
    fun `with every slot holding a flow none is empty`() {
        val full = current.copy(savedFlows = listOf(SavedFlow(0, "Fish", fishFlow), SavedFlow(1, "Oaks", fishFlow)))

        assertNull(saved.firstEmpty(full))
    }

    @Test
    fun `there is at least one slot`() {
        assertThrows<IllegalArgumentException> { SavedFlows(slots = 0) }
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }

    @Test
    fun `saving keeps the flow's reflexes with its steps`() {
        val state = saved.save(current.copy(reflexes = eat), slot = 1, name = "Cows")

        assertEquals(listOf(SavedFlow(1, "Cows", chopFlow, eat)), state.savedFlows)
    }

    @Test
    fun `loading takes the saved flow's reflexes`() {
        val before = current.copy(reflexes = emptyList(), savedFlows = listOf(SavedFlow(0, "Cows", fishFlow, eat)))

        assertEquals(eat, saved.load(before, slot = 0).reflexes)
    }

    @Test
    fun `a new flow has no reflexes`() {
        assertEquals(emptyList<ReflexSettings>(), saved.startNew(current.copy(reflexes = eat), slot = 1).reflexes)
    }

    @Test
    fun `emptying the current flow's slot empties its reflexes`() {
        val before = current.copy(reflexes = eat, savedFlows = listOf(SavedFlow(1, "Cows", chopFlow, eat)), savedSlot = 1)

        assertEquals(emptyList<ReflexSettings>(), saved.empty(before, slot = 1).reflexes)
    }

    @Test
    fun `a flow whose reflexes changed since it was saved has changed`() {
        val state = saved.save(current, slot = 0, name = "Cows").copy(reflexes = eat)

        assertTrue(saved.changedSinceSaved(state))
        assertEquals("Cows (changed)", saved.label(state))
    }
}
