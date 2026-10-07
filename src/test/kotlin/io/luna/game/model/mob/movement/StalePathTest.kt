package io.luna.game.model.mob.movement

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.path.GamePathfinder
import io.luna.game.model.path.PathResult
import io.luna.game.model.path.PlayerPathfinder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque

/** A path that arrives after its walk ended (the walk cancelled, another one clicked) must not move the player. */
class StalePathTest {

    private val start = Position(3200, 3200)
    private val goal = Position(3210, 3200)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    /** Finds the real path, but runs [meanwhile] first: what happens on the game thread while an async search runs. */
    private class SearchingWhile(player: Player, private val meanwhile: () -> Unit) :
        GamePathfinder<Position>(player.world.collisionManager) {

        override fun find(origin: Position, target: Position): PathResult<Position> {
            meanwhile()
            return PlayerPathfinder(collisionManager, origin.z).find(origin, target)
        }
    }

    private fun walkingTo(): Player {
        val player = TestWorld.login("navigator", start)
        player.navigator.navigate(goal, false)
        return player
    }

    /** What a minimap click does: the walk packet closes windows and interrupts weak actions, then queues its path. */
    private fun click(player: Player, tile: Position) {
        player.overlays.closeWindows()
        player.walking.clear()
        player.walking.replacePath(ArrayDeque(listOf(tile)))
    }

    @Test
    fun `a path found for the walk still going is walked`() {
        val player = walkingTo()

        player.navigator.walk(goal, SearchingWhile(player) {}, false)

        assertEquals(10, player.walking.size())
    }

    @Test
    fun `a path found after its walk was cancelled is not walked`() {
        val player = walkingTo()

        player.navigator.walk(goal, SearchingWhile(player) { player.navigator.cancel() }, false)

        assertTrue(player.walking.isEmpty)
    }

    @Test
    fun `a path found after the player clicked a walk of their own leaves that walk`() {
        val player = walkingTo()

        player.navigator.walk(goal, SearchingWhile(player) { click(player, Position(3200, 3201)) }, false)

        assertEquals(1, player.walking.size())
    }
}
