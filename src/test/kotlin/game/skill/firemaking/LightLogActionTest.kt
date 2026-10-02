package game.skill.firemaking

import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Lighting logs from the backpack and, since the IdleRS fix, logs lying on the ground. */
class LightLogActionTest {

    private val tile = Position(3200, 3200)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    /** At level 43 and above the first chance always lights the fire. */
    private fun firemaker(): Player =
        TestWorld.login("firemaker", tile).also {
            it.inventory.add(Item(Firemaking.TINDERBOX))
            it.skills.getSkill(Skill.FIREMAKING).level = CERTAIN_LEVEL
        }

    /** The tick of the click, then the ticks to the first chance (see `LightActionTest`). */
    private fun waitForTheFire() = TestWorld.tick(1 + Firemaking.FIRST_ATTEMPT_TICKS)

    private companion object {
        const val CERTAIN_LEVEL = 43
    }

    private fun fireAt(position: Position): Boolean =
        TestWorld.world.objects.findAll(position).anyMatch { it.id == Firemaking.FIRE_OBJECT }

    @Test
    fun `logs on the ground under the player are lit`() {
        val player = firemaker()
        val logs = TestWorld.dropItem(Log.NORMAL.id, 1, tile)

        player.submitAction(LightLogAction(player, Log.NORMAL, logs))
        waitForTheFire()

        assertTrue(fireAt(tile))
    }

    @Test
    fun `logs lit on the ground are used up`() {
        val player = firemaker()
        val logs = TestWorld.dropItem(Log.NORMAL.id, 1, tile)

        player.submitAction(LightLogAction(player, Log.NORMAL, logs))
        waitForTheFire()

        assertEquals(emptyList<Int>(), TestWorld.world.items.filter { it.position == tile }.map { it.id })
    }

    @Test
    fun `the fire catching is announced`() {
        val player = firemaker()
        player.inventory.add(Item(Log.NORMAL.id))

        player.submitAction(LightLogAction(player, Log.NORMAL, groundLog = null))
        waitForTheFire()

        assertTrue("The fire catches and the logs begin to burn." in TestWorld.chatbox(player))
    }

    @Test
    fun `logs from the backpack are still lit`() {
        val player = firemaker()
        player.inventory.add(Item(Log.NORMAL.id))

        player.submitAction(LightLogAction(player, Log.NORMAL, groundLog = null))
        waitForTheFire()

        assertTrue(fireAt(tile))
    }
}
