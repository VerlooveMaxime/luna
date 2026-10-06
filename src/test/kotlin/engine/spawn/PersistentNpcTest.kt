package engine.spawn

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.movement.wandering.DumbWanderingAction
import io.luna.game.model.mob.movement.wandering.WanderingFrequency
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PersistentNpcTest {

    private val giantRat = 950
    private val runescapeGuide = 945
    private val tile = Position(3230, 3230)

    /** A [PersistentNpc] takes its context from `api.predef`, which the test world binds. */
    @BeforeEach
    fun bindContext() {
        TestWorld.context
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun spawn(npc: PersistentNpc): PersistentNpc {
        check(TestWorld.world.npcs.add(npc)) { "the world refused npc ${npc.id}" }
        return npc
    }

    @Test
    fun `the spawn file's respawn delay wins`() {
        assertEquals(30, spawn(PersistentNpc(giantRat, tile, respawnAfter = 30)).respawnTicks)
    }

    @Test
    fun `without one in the spawn file the combat definition's respawn delay holds`() {
        assertEquals(60, spawn(PersistentNpc(giantRat, tile)).respawnTicks)
    }

    @Test
    fun `an npc with no respawn delay anywhere respawns after the default`() {
        assertEquals(PersistentNpc.DEFAULT_RESPAWN_TICKS, spawn(PersistentNpc(runescapeGuide, tile)).respawnTicks)
    }

    @Test
    fun `a respawned npc wanders again`() {
        val npc = spawn(PersistentNpc(giantRat, tile, wanderingRadius = 5, wanderingFrequency = WanderingFrequency.NORMAL))

        val respawn = spawn(npc.createRespawn() as PersistentNpc)

        assertTrue(respawn.actions.contains(DumbWanderingAction::class.java))
    }

    @Test
    fun `a respawned npc keeps the spawn file's respawn delay`() {
        val npc = spawn(PersistentNpc(giantRat, tile, respawnAfter = 30))

        assertEquals(30, spawn(npc.createRespawn() as PersistentNpc).respawnTicks)
    }
}
