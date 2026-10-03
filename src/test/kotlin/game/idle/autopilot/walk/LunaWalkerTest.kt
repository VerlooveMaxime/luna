package game.idle.autopilot.walk

import engine.widget.skill.LevelUpData
import engine.widget.skill.LevelUpInterface
import game.idle.location.Tile
import game.idle.movement.approachTiles
import game.testworld.TestWorld
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaWalkerTest {

    private val start = Position(3200, 3200)
    private val target = Tile(3210, 3200)
    private val deadTreeId = 1286

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("walker", start)

    @Test
    fun `an idle player is not busy`() {
        assertFalse(LunaWalker(login(), target).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(LunaWalker(player, target).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(LunaWalker(player, target).isBusy())
    }

    @Test
    fun `a level-up dialogue does not keep the player busy`() {
        val player = login()
        player.overlays.open(LevelUpInterface(Skill.WOODCUTTING, 2, LevelUpData(4273, 4274, 4272)))

        assertFalse(LunaWalker(player, target).isBusy())
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
    fun `walking to a taken tile heads for the nearest free tile next to it`() {
        val player = login()
        TestWorld.place(deadTreeId, target.toPosition())

        LunaWalker(player, target).walk()

        assertEquals(Position(3209, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `walking to a taken tile with no free tile next to it heads for the tile itself`() {
        val player = login()
        val goal = target.toPosition()
        (approachTiles(goal, size = 1, from = start) + goal).forEach { TestWorld.place(deadTreeId, it) }

        LunaWalker(player, target).walk()

        assertEquals(goal, player.navigator.currentTarget)
    }

    @Test
    fun `walking closes the open window like a click`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        LunaWalker(player, target).walk()

        assertFalse(OverlayType.WIDGET_STANDARD in player.overlays.overlayMap)
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val player = login()

        LunaWalker(player, target).tell("On my way.")

        assertEquals(listOf("On my way."), TestWorld.chatbox(player))
    }
}
