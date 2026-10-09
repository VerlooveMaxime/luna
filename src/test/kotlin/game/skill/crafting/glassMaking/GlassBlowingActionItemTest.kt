package game.skill.crafting.glassMaking

import api.predef.*
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Since the IdleRS fix, glass blowing checks the material's Crafting level. */
class GlassBlowingActionItemTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun blower(level: Int): Player =
        TestWorld.login("blower", Position(3200, 3200)).also {
            it.crafting.level = level
            it.inventory.add(Item(GlassMaterial.PIPE))
            it.inventory.add(Item(GlassMaterial.MOLTEN_GLASS))
        }

    private fun blowVial(player: Player): List<Int> {
        player.submitAction(GlassBlowingActionItem(player, GlassMaterial.VIAL, 1))
        TestWorld.tick(3)
        return player.inventory.filterNotNull().map { it.id }
    }

    @Test
    fun `below the material's level the glass is not blown`() {
        val player = blower(GlassMaterial.VIAL.level - 1)

        assertEquals(listOf(GlassMaterial.PIPE, GlassMaterial.MOLTEN_GLASS), blowVial(player))
        assertEquals(listOf("You need a Crafting level of 33 to make this."), TestWorld.chatbox(player))
    }

    @Test
    fun `at the material's level the glass is blown`() {
        val player = blower(GlassMaterial.VIAL.level)

        assertEquals(listOf(GlassMaterial.PIPE, GlassMaterial.VIAL.id), blowVial(player))
    }
}
