package game.skill.cooking.prepareFood

import api.predef.*
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The containers food prep gives back: since the IdleRS fix, only those the product leaves. */
class PrepareFoodActionItemTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun cook(vararg items: Int): Player =
        TestWorld.login("cook", Position(3200, 3200)).also { player ->
            player.cooking.level = 99
            items.forEach { player.inventory.add(Item(it)) }
        }

    /** Prepares [food] once from [used] on [target], as Luna's window does. */
    private fun prepare(player: Player, food: IncompleteFood, used: Int, target: Int): List<Int> {
        player.submitAction(PrepareFoodActionItem(player, food, mutableSetOf(used, target), 1))
        TestWorld.tick(3)
        return player.inventory.filterNotNull().map { it.id }.sorted()
    }

    @Test
    fun `an incomplete stew stays in its bowl, so no empty bowl comes back`() {
        val player = cook(BOWL_OF_WATER, POTATO)

        assertEquals(listOf(IncompleteFood.INCOMPLETE_STEW_WITH_POTATO.id), prepare(player, IncompleteFood.INCOMPLETE_STEW_WITH_POTATO, BOWL_OF_WATER, POTATO))
    }

    @Test
    fun `nettle-water stays in its bowl`() {
        val player = cook(BOWL_OF_WATER, NETTLES)

        assertEquals(listOf(IncompleteFood.NETTLE_WATER.id), prepare(player, IncompleteFood.NETTLE_WATER, BOWL_OF_WATER, NETTLES))
    }

    @Test
    fun `milky nettle tea stays in its bowl and gives the milk's bucket back`() {
        val player = cook(NETTLE_TEA, BUCKET_OF_MILK)

        assertEquals(listOf(BUCKET, IncompleteFood.MILKY_NETTLE_TEA.id), prepare(player, IncompleteFood.MILKY_NETTLE_TEA, NETTLE_TEA, BUCKET_OF_MILK))
    }

    @Test
    fun `nettle tea poured into a cup gives its bowl back`() {
        val player = cook(EMPTY_CUP, NETTLE_TEA)

        assertEquals(listOf(BOWL, IncompleteFood.CUP_OF_NETTLE_TEA.id), prepare(player, IncompleteFood.CUP_OF_NETTLE_TEA, EMPTY_CUP, NETTLE_TEA))
    }

    @Test
    fun `milk is added to a cup of nettle tea, not to an empty cup`() {
        assertEquals(IncompleteFood.CUP_OF_NETTLE_TEA.id, IncompleteFood.CUP_OF_MILKY_NETTLE_TEA.baseIngredient)
    }

    @Test
    fun `bread dough gives the water's bucket and the flour's pot back`() {
        val player = cook(POT_OF_FLOUR, BUCKET_OF_WATER)

        assertEquals(listOf(BUCKET, POT, IncompleteFood.BREAD_DOUGH.id), prepare(player, IncompleteFood.BREAD_DOUGH, POT_OF_FLOUR, BUCKET_OF_WATER))
    }

    private companion object {
        const val BOWL_OF_WATER = 1921
        const val BOWL = 1923
        const val POTATO = 1942
        const val NETTLES = 4241
        const val NETTLE_TEA = 4239
        const val BUCKET_OF_MILK = 1927
        const val BUCKET = 1925
        const val EMPTY_CUP = 1980
        const val POT_OF_FLOUR = 1933
        const val POT = 1931
        const val BUCKET_OF_WATER = 1929
    }
}
