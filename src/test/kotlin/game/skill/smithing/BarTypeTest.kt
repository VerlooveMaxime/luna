package game.skill.smithing

import game.skill.smithing.smeltOre.SmeltAction
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Luna gave a smelted bar the smithing experience per bar; IdleRS keeps the 2006 smelting values (LostCity's 377
 * `smelting.struct`), and smithing keeps its own.
 */
class BarTypeTest {

    /** The enum names its ores from the item definitions, which the test world loads. */
    @BeforeEach
    fun loadDefinitions() {
        TestWorld.world
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `each bar smelts for its 2006 experience`() {
        val expected = mapOf(
            BarType.BRONZE to 6.2,
            BarType.IRON to 12.5,
            BarType.STEEL to 17.5,
            BarType.SILVER to 13.7,
            BarType.GOLD to 22.5,
            BarType.MITHRIL to 30.0,
            BarType.ADAMANT to 37.5,
            BarType.RUNE to 50.0,
        )

        assertEquals(expected, BarType.entries.associateWith { it.smeltXp })
    }

    @Test
    fun `smelting a bronze bar gives the smelting experience`() {
        val player = TestWorld.login("smelter", Position(3200, 3200))
        player.inventory.add(Item(436))
        player.inventory.add(Item(438))

        player.submitAction(SmeltAction(player, BarType.BRONZE, 1))
        TestWorld.tick(2)

        assertEquals(6.2, player.skills.getSkill(Skill.SMITHING).experience)
    }

    @Test
    fun `Tutorial Island's furnace counts as a furnace`() {
        assertTrue(Smithing.TUTORIAL_FURNACE in Smithing.FURNACE_OBJECTS)
    }
}
