package game.idle.autopilot.walk

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalkStepTypeTest {

    private val needsTile = "walk needs a tile: walk <x> <y>, or walk <x> <y> <floor>"

    @Test
    fun `a walk line names its tile`() {
        assertEquals(listOf("3086 3233"), WalkStepType.parse(listOf("3086", "3233")))
        assertEquals("walk 3086 3233", WalkStepType.line(listOf("3086 3233")))
    }

    @Test
    fun `a ground floor written out is left out again`() {
        assertEquals(listOf("3086 3233"), WalkStepType.parse(listOf("3086", "3233", "0")))
    }

    @Test
    fun `an upper floor is kept`() {
        assertEquals(listOf("3086 3233 1"), WalkStepType.parse(listOf("3086", "3233", "1")))
    }

    @Test
    fun `a walk line needs two or three numbers`() {
        assertRejected(needsTile) { WalkStepType.parse(emptyList()) }
        assertRejected(needsTile) { WalkStepType.parse(listOf("3086")) }
        assertRejected(needsTile) { WalkStepType.parse(listOf("3086", "north")) }
        assertRejected(needsTile) { WalkStepType.parse(listOf("1", "2", "3", "4")) }
    }

    @Test
    fun `a tile lies on the map`() {
        assertRejected("A tile has no negative coordinates: walk -1 5") { WalkStepType.parse(listOf("-1", "5")) }
        assertRejected("A tile has no negative coordinates: walk 5 -1") { WalkStepType.parse(listOf("5", "-1")) }
        assertRejected("The floor is 0 to 3, not 4") { WalkStepType.parse(listOf("5", "5", "4")) }
        assertRejected("The floor is 0 to 3, not -1") { WalkStepType.parse(listOf("5", "5", "-1")) }
    }

    @Test
    fun `a walk step goes to its tile`() {
        assertEquals(WalkStep(Tile(3086, 3233, 1)), WalkStepType.resolve(listOf("3086 3233 1"), FlowContext()))
    }

    @Test
    fun `a blank tile from the builder is rejected`() {
        assertRejected(needsTile) { WalkStepType.resolve(listOf(""), FlowContext()) }
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
}
