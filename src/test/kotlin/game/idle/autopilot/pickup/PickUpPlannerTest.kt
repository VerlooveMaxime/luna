package game.idle.autopilot.pickup

import game.idle.autopilot.pickup.PickUpDecision.BagFull
import game.idle.autopilot.pickup.PickUpDecision.Done
import game.idle.autopilot.pickup.PickUpDecision.Take
import game.idle.autopilot.pickup.PickUpDecision.WalkTo
import game.idle.autopilot.pickup.PickUpDecision.WalkToLocation
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PickUpPlannerTest {

    private val bones = 526
    private val cowhide = 1739

    private fun loot(id: Int, x: Int, distance: Int, fits: Boolean = true, y: Int = 3200) = GroundLoot(id, Position(x, y), distance, fits)

    private fun decide(vararg items: GroundLoot, atLocation: Boolean = true) = PickUpPlanner.decide(PickUpView(atLocation, items.toList()))

    @Test
    fun `an item underfoot is taken`() {
        val underfoot = loot(bones, 3200, distance = 0)

        assertEquals(Take(underfoot), decide(underfoot))
    }

    @Test
    fun `the nearest item by walking is walked to`() {
        val near = loot(bones, 3205, distance = 2)

        assertEquals(WalkTo(near), decide(loot(cowhide, 3201, distance = 6), near))
    }

    @Test
    fun `items as near are taken west to east`() {
        val west = loot(bones, 3201, distance = 2)

        assertEquals(WalkTo(west), decide(loot(cowhide, 3203, distance = 2), west))
    }

    @Test
    fun `items as near on one column are taken south to north`() {
        val south = loot(bones, 3201, distance = 2, y = 3199)

        assertEquals(WalkTo(south), decide(loot(cowhide, 3201, distance = 2, y = 3201), south))
    }

    @Test
    fun `an item that does not fit the bag is passed over`() {
        val fitting = loot(cowhide, 3204, distance = 4)

        assertEquals(WalkTo(fitting), decide(loot(bones, 3200, distance = 0, fits = false), fitting))
    }

    @Test
    fun `items lying with none fitting fill the bag, each kind named once`() {
        val left = decide(loot(bones, 3201, distance = 1, fits = false), loot(bones, 3202, distance = 2, fits = false), loot(cowhide, 3203, distance = 3, fits = false))

        assertEquals(BagFull(listOf(bones, cowhide)), left)
    }

    @Test
    fun `nothing lying away from the area walks there`() {
        assertEquals(WalkToLocation, decide(atLocation = false))
    }

    @Test
    fun `nothing lying in the area is done`() {
        assertEquals(Done, decide())
    }
}
