package game.idle.autopilot.woodcutting

import game.idle.flow.option.FakeNames
import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TreeOptionsTest {

    private val rows = TreeOptions(FakeNames()).options(OptionContext(OptionFacts(levels = mapOf(Skill.WOODCUTTING to 29))))

    @Test
    fun `every tree a chop step can cut is a row, under the word its setting keeps`() {
        assertEquals(ChopStepType.CUTTABLE.map { it.name.lowercase() }, rows.map { it.value })
    }

    @Test
    fun `a tree below the player's level can be picked`() {
        assertNull(rows.single { it.value == "oak" }.blocked)
    }

    @Test
    fun `a tree above the player's level is greyed`() {
        assertEquals("needs Woodcutting 30", rows.single { it.value == "willow" }.blocked)
    }

    @Test
    fun `a tree shows its logs`() {
        assertEquals(OptionIcon.Item(1519), rows.single { it.value == "willow" }.icon)
    }

    @Test
    fun `a tree is named as the cache names the standing tree`() {
        TestWorld.context

        val labels = TreeOptions(LunaGameNames).options(OptionContext()).map { it.label }

        assertEquals(listOf("Tree", "Oak", "Willow"), labels.take(3))
    }
}
