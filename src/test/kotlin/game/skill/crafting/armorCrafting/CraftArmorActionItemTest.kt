package game.skill.crafting.armorCrafting

import api.predef.*
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.threadUsed
import game.skill.crafting.hideTanning.Hide
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Since the IdleRS fixes, leather armour gives its experience and a reel of thread lasts five items. */
class CraftArmorActionItemTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun crafter(leather: Int, thread: Int = 1): Player =
        TestWorld.login("crafter", Position(3200, 3200)).also { player ->
            player.inventory.add(Item(CraftArmorActionItem.NEEDLE_ID))
            player.inventory.add(Item(CraftArmorActionItem.THREAD_ID, thread))
            repeat(leather) { player.inventory.add(Item(Hide.SOFT_LEATHER.tan)) }
        }

    /** Crafts up to [times] pairs of gloves, four ticks each. */
    private fun craftGloves(player: Player, times: Int) {
        player.submitAction(CraftArmorActionItem(player, HideArmor.LEATHER_GLOVES, times))
        TestWorld.tick(4 * times + 2)
    }

    private fun count(player: Player, id: Int): Int = player.inventory.computeAmountForId(id)

    @Test
    fun `crafting leather gloves gives their experience`() {
        val player = crafter(leather = 1)

        craftGloves(player, 1)

        assertEquals(HideArmor.LEATHER_GLOVES.exp, player.crafting.experience, 0.001)
    }

    @Test
    fun `a reel of thread makes five items before it is used up`() {
        val player = crafter(leather = 6)

        craftGloves(player, 6)

        assertEquals(listOf(5, 0), listOf(count(player, HideArmor.LEATHER_GLOVES.id), count(player, CraftArmorActionItem.THREAD_ID)))
    }

    @Test
    fun `a reel is kept until its fifth item`() {
        val player = crafter(leather = 4)

        craftGloves(player, 4)

        assertEquals(listOf(1, 4), listOf(count(player, CraftArmorActionItem.THREAD_ID), player.threadUsed))
    }

    @Test
    fun `the reel's fifth item uses it up and says so`() {
        val player = crafter(leather = 1)
        player.threadUsed = 4

        craftGloves(player, 1)

        assertEquals(listOf(0, 0), listOf(count(player, CraftArmorActionItem.THREAD_ID), player.threadUsed))
        assertEquals("You use up one of your reels of thread.", TestWorld.chatbox(player).last())
    }

    @Test
    fun `running out of leather uses no thread`() {
        val player = crafter(leather = 2)

        craftGloves(player, 5)

        assertEquals(2, player.threadUsed)
    }
}
