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

    /** A tunnel used from just south of [outside], landing four tiles east, past the wilderness line when there is one. */
    private val tunnel = EntranceStep(Doorway(6912, Position(3200, 3198), EdgeType.ENTRANCE), Position(3200, 3199), Position(3204, 3199), ticks = 5.0)
    private val doorways = Doorways(mapOf((outside to inside) to door), listOf(tunnel))

    /**
     * Collision as [open] says for every step; the wilderness starts at x 3201 when [wild]; the player sees an option on
     * the tunnel when [shown].
     */
    private fun steps(open: Boolean, wild: Boolean = false, shown: Boolean = true) =
        ReachSteps({ _, _ -> open }, doorways, inWilderness = { wild && it.x >= 3201 }, showsOption = { shown })

    @Test
    fun `a step collision lets through is a plain one`() {
        assertEquals(Step.Plain, steps(open = true).step(outside, Direction.NORTH))
    }

    @Test
    fun `a step through an open doorway names it, open`() {
        assertEquals(DoorStep(door, outside, inside, shut = false), steps(open = true).step(outside, Direction.EAST))
    }

    @Test
    fun `a step a shut doorway blocks goes through it, shut`() {
        assertEquals(DoorStep(door, inside, outside, shut = true), steps(open = false).step(inside, Direction.WEST))
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
        assertEquals(DoorStep(door, inside, outside, shut = false), steps(open = true, wild = true).step(inside, Direction.WEST))
    }

    @Test
    fun `a shut door costs six steps more than an open one`() {
        assertEquals(listOf(7, 1, 1), listOf(DoorStep(door, outside, inside, shut = true).cost, DoorStep(door, outside, inside, shut = false).cost, Step.Plain.cost))
    }

    @Test
    fun `a walk goes through an entrance used from where it stands`() {
        assertEquals(listOf(tunnel), steps(open = true).entrances(tunnel.from))
    }

    @Test
    fun `a walk goes through no entrance away from where one is used`() {
        assertEquals(emptyList<EntranceStep>(), steps(open = true).entrances(outside))
    }

    @Test
    fun `a walk goes through no entrance the player sees no option on`() {
        assertEquals(emptyList<EntranceStep>(), steps(open = true, shown = false).entrances(tunnel.from))
    }

    @Test
    fun `a walk goes through no entrance into the wilderness`() {
        assertEquals(emptyList<EntranceStep>(), steps(open = true, wild = true).entrances(tunnel.from))
    }

    @Test
    fun `an entrance costs its ticks at a run`() {
        assertEquals(10, tunnel.cost)
    }

    @Test
    fun `an entrance's length is how far it moves the player`() {
        assertEquals(4, tunnel.length)
    }

    @Test
    fun `a walk built of steps alone goes through no entrance`() {
        assertEquals(emptyList<EntranceStep>(), Walk { _, _ -> Step.Plain }.entrances(tunnel.from))
    }
}
