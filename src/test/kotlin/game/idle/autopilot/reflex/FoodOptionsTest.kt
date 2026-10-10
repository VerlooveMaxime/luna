package game.idle.autopilot.reflex

import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.player.item.consume.food.Food
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoodOptionsTest {

    // Luna's Food names its items when it loads, which needs the cache: the world loads first (coverage notes).
    private val world = TestWorld.world
    private val cake = 1891
    private val twoThirds = 1893
    private val source = FoodOptions(FakeNames(mapOf(cake to "Cake")))

    private fun cakeRow(bank: Map<Int, Int>) = source.options(OptionContext(OptionFacts(bank = bank))).single { it.value == "$cake" }

    @Test
    fun `every food is offered once, by its first portion`() {
        assertEquals(Food.entries.map { "${it.id}" }, source.options(OptionContext()).map { it.value })
    }

    @Test
    fun `a food's row says what it heals and how much the bank holds, every portion counted`() {
        val row = cakeRow(mapOf(cake to 2, twoThirds to 1))

        assertEquals(listOf("Cake", OptionIcon.Item(cake), "heals 4, 3 in bank", 3), listOf(row.label, row.icon, row.note, row.banked))
    }

    @Test
    fun `a food the bank lacks says none`() {
        assertEquals("heals 4, none in bank", cakeRow(emptyMap()).note)
    }

    @Test
    fun `every portion maps to all of its food's portions`() {
        assertEquals(setOf(cake, twoThirds, 1895), FoodOptions.portions()[twoThirds])
    }
}
