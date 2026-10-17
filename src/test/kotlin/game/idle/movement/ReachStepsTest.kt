package game.idle.movement

import api.bot.webwalk.model.EdgeType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReachStepsTest {

    private val outside = Position(3200, 3200)
    private val inside = Position(3201, 3200)
    private val door = Doorway(1530, inside, EdgeType.DOOR)
    private val doorways = Doorways(mapOf((outside to inside) to door))

    /** Collision as [open] says for every step; the wilderness starts at x 3201 when [wild]. */
    private fun steps(open: Boolean, wild: Boolean = false) =
        ReachSteps({ _, _ -> open }, doorways, inWilderness = { wild && it.x >= 3201 })

    @Test
    fun `a step collision lets through is a plain one`() {
        assertEquals(Step.Plain, steps(open = true).step(outside, Direction.NORTH))
    }

    @Test
    fun `a step through an open doorway names it, open`() {
        assertEquals(DoorStep(door, outside, shut = false), steps(open = true).step(outside, Direction.EAST))
    }

    @Test
    fun `a step a shut doorway blocks goes through it, shut`() {
        assertEquals(DoorStep(door, inside, shut = true), steps(open = false).step(inside, Direction.WEST))
    }

    @Test
    fun `a step a wall blocks is none`() {
        assertNull(steps(open = false).step(outside, Direction.NORTH))
    }

    @Test
    fun `a diagonal step past a shut doorway is none`() {
        assertNull(steps(open = false).step(outside, Direction.NORTH_EAST))
    }

    @Test
    fun `no step goes from outside the wilderness into it`() {
        assertNull(steps(open = true, wild = true).step(outside, Direction.EAST))
    }

    @Test
    fun `a step out of the wilderness is taken`() {
        assertEquals(DoorStep(door, inside, shut = false), steps(open = true, wild = true).step(inside, Direction.WEST))
    }

    @Test
    fun `a shut door costs six steps more than an open one`() {
        assertEquals(listOf(7, 1, 1), listOf(DoorStep(door, outside, shut = true).cost, DoorStep(door, outside, shut = false).cost, Step.Plain.cost))
    }
}
