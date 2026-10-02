package game.skill.firemaking

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The rhythm of striking a tinderbox, with the fire's luck decided by the test. */
class LightActionTest {

    /** Lights on the strikes [outcomes] allow, in order; counts the fires. */
    private class Scripted(player: Player, private val outcomes: ArrayDeque<Boolean>) : LightAction(player) {
        var fires = 0

        override fun catches(): Boolean = outcomes.removeFirst()

        override fun onLight() {
            fires++
        }
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun firemaker(): Player =
        TestWorld.login("firemaker", Position(3200, 3200)).also { it.inventory.add(Item(Firemaking.TINDERBOX)) }

    private fun light(player: Player, vararg outcomes: Boolean): Scripted =
        Scripted(player, ArrayDeque(outcomes.toList())).also { player.submitAction(it) }

    /**
     * A click lands at the start of a tick and its action is first processed in that same tick; here the action is
     * submitted between ticks, so the first tick driven stands for the tick of the click.
     */
    private fun ticksAfterTheStrike(ticks: Int) = TestWorld.tick(CLICK_TICK + ticks)

    private companion object {
        const val CLICK_TICK = 1
    }

    @Test
    fun `the fire cannot catch before the first chance`() {
        val action = light(firemaker(), true)

        ticksAfterTheStrike(Firemaking.FIRST_ATTEMPT_TICKS - 1)

        assertEquals(0, action.fires)
    }

    @Test
    fun `the first chance comes three ticks after the first strike`() {
        val action = light(firemaker(), true)

        ticksAfterTheStrike(Firemaking.FIRST_ATTEMPT_TICKS)

        assertEquals(1, action.fires)
    }

    @Test
    fun `a strike that fails is tried again four ticks later`() {
        val action = light(firemaker(), false, true)

        ticksAfterTheStrike(Firemaking.FIRST_ATTEMPT_TICKS + Firemaking.ATTEMPT_TICKS)

        assertEquals(1, action.fires)
    }

    @Test
    fun `nothing catches between two strikes`() {
        val action = light(firemaker(), false, true)

        ticksAfterTheStrike(Firemaking.FIRST_ATTEMPT_TICKS + Firemaking.ATTEMPT_TICKS - 1)

        assertEquals(0, action.fires)
    }

    @Test
    fun `without a tinderbox nothing is struck`() {
        val player = TestWorld.login("firemaker", Position(3200, 3200))

        light(player, true)

        assertTrue("You need a tinderbox to light this." in TestWorld.chatbox(player))
    }
}
