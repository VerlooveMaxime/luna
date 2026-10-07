package game.idle.autopilot.mining

import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Luna's ores name their items from the cache, so these run on [TestWorld]. */
class RockOptionsTest {

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun rows() = RockOptions(LunaGameNames).options(OptionContext(OptionFacts(levels = mapOf(Skill.MINING to 14))))

    @Test
    fun `every ore a mine step can mine is a row, under the word its setting keeps`() {
        assertEquals(MineStepType.MINEABLE.map { it.name.lowercase() }, rows().map { it.value })
    }

    @Test
    fun `a rock is named after its ore`() {
        assertEquals("Copper ore", rows().single { it.value == "copper" }.label)
    }

    @Test
    fun `a rock below the player's level can be picked`() {
        assertNull(rows().single { it.value == "tin" }.blocked)
    }

    @Test
    fun `a rock above the player's level is greyed`() {
        assertEquals("needs Mining 15", rows().single { it.value == "iron" }.blocked)
    }
}
