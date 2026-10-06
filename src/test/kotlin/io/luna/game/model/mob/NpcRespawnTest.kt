package io.luna.game.model.mob

import game.testworld.TestWorld
import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class NpcRespawnTest {

    private val giantRat = 950
    private val tile = Position(3230, 3230)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun respawnOf(npc: Npc): Npc {
        npc.move(tile.translate(2, 2))
        return npc.createRespawn()
    }

    @Test
    fun `a respawn comes back as the same npc`() {
        assertEquals(giantRat, respawnOf(TestWorld.spawnNpc(giantRat, tile)).id)
    }

    @Test
    fun `a respawn comes back where the npc first spawned`() {
        assertEquals(tile, respawnOf(TestWorld.spawnNpc(giantRat, tile)).position)
    }

    @Test
    fun `a respawn keeps the respawn delay`() {
        val npc = TestWorld.spawnNpc(giantRat, tile)
        npc.respawnTicks = 25

        assertEquals(25, respawnOf(npc).respawnTicks)
    }

    @Test
    fun `a respawn keeps the direction a stationary npc faces`() {
        val npc = TestWorld.spawnNpc(giantRat, tile)
        npc.defaultDirection = Optional.of(Direction.EAST)

        assertEquals(Optional.of(Direction.EAST), respawnOf(npc).defaultDirection)
    }
}
