package io.luna.game.model.collision

import game.idle.movement.canStandOn
import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpcCollisionTest {

    private val chef = 942
    private val deadTreeId = 1286
    private val tile = Position(3230, 3230)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `players can step onto the tile an npc spawned on`() {
        TestWorld.spawnNpc(chef, tile)

        assertTrue(canStandOn(TestWorld.world.collisionManager, tile))
    }

    @Test
    fun `removing an npc keeps the collision of what stands under it`() {
        TestWorld.place(deadTreeId, tile)
        val npc = TestWorld.spawnNpc(chef, tile)

        TestWorld.world.npcs.remove(npc)

        assertFalse(canStandOn(TestWorld.world.collisionManager, tile))
    }
}
