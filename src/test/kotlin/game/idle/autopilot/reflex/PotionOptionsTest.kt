package game.idle.autopilot.reflex

import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.player.item.consume.potion.Potion
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PotionOptionsTest {

    private val world = TestWorld.world
    private val source = PotionOptions(FakeNames(mapOf(113 to "Strength potion(4)")))

    @Test
    fun `every Luna potion is offered once by its full vial`() {
        assertEquals(Potion.entries.map { it.fourDose.toString() }, source.options(OptionContext()).map { it.value })
    }

    @Test
    fun `the bank note counts doses across all vial sizes`() {
        val row = source.options(OptionContext(OptionFacts(bank = mapOf(113 to 2, 115 to 1, 117 to 2, 119 to 3)))).single { it.value == "113" }

        assertEquals(listOf("Strength potion", OptionIcon.Item(113), "18 doses in bank", 18), listOf(row.label, row.icon, row.note, row.banked))
    }

    @Test
    fun `an absent potion still appears with zero doses`() {
        assertEquals("0 doses in bank", source.options(OptionContext()).single { it.value == "113" }.note)
    }

    @Test
    fun `the potion map puts its doses in increasing order`() {
        assertEquals(listOf(119, 117, 115, 113), PotionOptions.doses()[113])
    }
}
