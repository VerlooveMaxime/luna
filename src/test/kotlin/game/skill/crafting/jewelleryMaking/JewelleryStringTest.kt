package game.skill.crafting.jewelleryMaking

import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JewelleryStringTest {

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun name(id: Int): String = ItemDefinition.ALL.retrieve(id).name

    @Test
    fun `the amulets and the two holy symbols are what a ball of wool strings`() {
        val strung = GoldJewelleryTable.AMULETS.jewelleryItems.map { it.item.id } +
            SilverJewelleryTable.SARADOMIN_SYMBOL.jewelleryItem.item.id + SilverJewelleryTable.ZAMORAK_SYMBOL.jewelleryItem.item.id

        assertEquals(strung, JewelleryString.UNSTRUNG_TO_STRUNG.keys.toList())
    }

    @Test
    fun `a strung amulet keeps its name, and the symbols become unblessed and unpowered`() {
        val names = JewelleryString.UNSTRUNG_TO_STRUNG.values.map(::name)

        assertEquals(
            listOf("Gold amulet", "Sapphire amulet", "Emerald amulet", "Ruby amulet", "Diamond amulet", "Dragonstone ammy",
                   "Onyx amulet", "Unblessed symbol", "Unpowered symbol"),
            names,
        )
    }

    @Test
    fun `stringing takes a ball of wool`() {
        assertEquals("Ball of wool", name(JewelleryString.BALL_OF_WOOL))
    }
}
