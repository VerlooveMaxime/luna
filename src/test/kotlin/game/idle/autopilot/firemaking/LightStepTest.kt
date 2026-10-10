package game.idle.autopilot.firemaking

import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LightStepTest {

    private val tinderbox = 590
    private val logs = 1511
    private val fire = 2732
    private val standing = Position(3205, 3200)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    /** A player with a tinderbox and logs, standing on a fire, so the step looks for a free tile first. */
    private fun onAFire(): Player {
        val player = TestWorld.login("light", standing)
        player.inventory.add(Item(tinderbox))
        player.inventory.add(Item(logs))
        TestWorld.place(fire, standing)
        return player
    }

    @Test
    fun `a light step with a picked tile looks for a free tile from it`() {
        val player = onAFire()

        LightStep(setOf(logs), spot = Tile(3200, 3200)).activity(player, Tile(3190, 3190)).act()

        assertEquals(Position(3200, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `a light step lighting where it stands looks for a free tile next to the player`() {
        val player = onAFire()

        LightStep(setOf(logs)).activity(player, Tile(3190, 3190)).act()

        assertEquals(1, player.navigator.currentTarget?.let { maxOf(Math.abs(it.x - standing.x), Math.abs(it.y - standing.y)) })
    }
}
