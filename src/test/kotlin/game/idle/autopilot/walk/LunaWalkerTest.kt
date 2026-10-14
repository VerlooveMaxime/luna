package game.idle.autopilot.walk

import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaWalkerTest {

    private val start = Position(3200, 3200)
    private val target = Tile(3210, 3200)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("walker", start)

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(LunaWalker(player, target).isBusy())
    }

    @Test
    fun `the position is the player's tile`() {
        assertEquals(Tile(3200, 3200), LunaWalker(login(), target).position())
    }

    @Test
    fun `walking heads for a free target tile`() {
        val player = login()

        LunaWalker(player, target).walk()

        assertEquals(target.toPosition(), player.navigator.currentTarget)
    }

    @Test
    fun `interrupting breaks off the action in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        LunaWalker(player, target).interrupt()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
