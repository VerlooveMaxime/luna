package game.idle.autopilot.walk

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalkStepTypeTest {

    private fun walk(tile: String? = null) = StepSettings("walk", tile?.let { mapOf("tile" to it) } ?: emptyMap())

    private fun resolve(tile: String) = WalkStepType.resolve(walk(tile), FlowContext())

    @Test
    fun `a walk step reads as its tile`() {
        assertEquals("walk 3086 3233", WalkStepType.summary(walk("3086 3233")))
    }

    @Test
    fun `a walk step without a tile reads with a question mark and is rejected`() {
        assertEquals("walk ?", WalkStepType.summary(walk()))
        assertRejected("walk needs a tile") { WalkStepType.resolve(walk(), FlowContext()) }
    }

    @Test
    fun `a walk step goes to its tile, on the ground floor unless one is given`() {
        assertEquals(WalkStep(Tile(3086, 3233)), resolve("3086 3233"))
        assertEquals(WalkStep(Tile(3086, 3233, 1)), resolve(" 3086  3233 1 "))
    }

    @Test
    fun `a tile is two or three numbers`() {
        assertRejected("walk needs a tile: x y, or x y floor, not '3086'") { resolve("3086") }
        assertRejected("walk needs a tile: x y, or x y floor, not '3086 north'") { resolve("3086 north") }
        assertRejected("walk needs a tile: x y, or x y floor, not '1 2 3 4'") { resolve("1 2 3 4") }
    }

    @Test
    fun `a tile lies on the map`() {
        assertRejected("A tile has no negative coordinates: -1 5") { resolve("-1 5") }
        assertRejected("A tile has no negative coordinates: 5 -1") { resolve("5 -1") }
        assertRejected("The floor is 0 to 3, not 4") { resolve("5 5 4") }
        assertRejected("The floor is 0 to 3, not -1") { resolve("5 5 -1") }
    }

    @Test
    fun `the configure screen shows no level for a walk`() {
        assertNull(WalkStepType.skill(StepSettings("walk")))
    }

    @Test
    fun `the configure screen picks the tile on the map`() {
        assertEquals(listOf("Tile (left): map tile"), described(WalkStepType.fields(FakeNames())))
    }

    @Test
    fun `the steps after a walk step work where it went`() {
        val after = WalkStep(Tile(3086, 3233)).after(FlowContext(gathered = setOf(1511)))

        assertEquals(FlowContext(WorkSpot.At(Tile(3086, 3233)), setOf(1511)), after)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        val error = assertThrows<FlowError> { action() }

        assertEquals(message, error.message)
    }

    @Test
    fun `a walk step shows the minimap's red flag`() {
        assertEquals(StepIcon.Media("mapmarker", 0), WalkStepType.icon(StepSettings("walk")))
    }

    @Test
    fun `a walk step's slot says where it walks to`() {
        assertEquals(listOf("to 3086, 3233"), WalkStepType.details(walk("3086 3233"), FlowContext()))
    }

    @Test
    fun `a walk step without a tile says it is not set yet`() {
        assertEquals(listOf("not set yet"), WalkStepType.details(walk(), FlowContext()))
    }

    @Test
    fun `a walk step has no target`() {
        assertNull(WalkStepType.target(FakeNames()))
    }

    @Test
    fun `a walk step needs nothing in the bag`() {
        assertEquals(emptyList<StepNeeds>(), WalkStep(Tile(3086, 3233)).needs())
    }
}
