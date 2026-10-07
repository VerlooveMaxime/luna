package game.idle.autopilot.fighting

import game.testworld.TestWorld
import io.luna.game.model.def.NpcDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The tracked fight targets against the cache's npc definitions; skipped where the cache is absent. */
class FightTargetsDataTest {

    private val targets = FightTargetCatalog.load(FightTargetCatalog.PATH).targets

    private fun definition(id: Int): NpcDefinition {
        TestWorld.world
        return NpcDefinition.ALL.retrieve(id)
    }

    @Test
    fun `every npc of a target goes by the target's name`() {
        val misnamed = targets.flatMap { target -> target.npcs.filter { definition(it).name.lowercase() != target.name } }

        assertEquals(emptyList<Int>(), misnamed)
    }

    @Test
    fun `every npc of a target can be attacked`() {
        val peaceful = targets.flatMap { target -> target.npcs.filter { "Attack" !in definition(it).actions } }

        assertEquals(emptyList<Int>(), peaceful)
    }
}
