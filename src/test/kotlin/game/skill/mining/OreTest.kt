package game.skill.mining

import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Luna shipped 17.5 for most ores; IdleRS keeps the 2006 values (LostCity's 377 `mine.dbrow`). */
class OreTest {

    /** The enum names its ores from the item definitions, which the test world loads. */
    @BeforeEach
    fun loadDefinitions() {
        TestWorld.world
    }

    @Test
    fun `each ore gives its 2006 experience`() {
        val expected = mapOf(
            Ore.RUNE_ESSENCE to 5.0,
            Ore.PURE_ESSENCE to 5.0,
            Ore.CLAY to 5.0,
            Ore.TIN to 17.5,
            Ore.COPPER to 17.5,
            Ore.IRON to 35.0,
            Ore.SILVER to 40.0,
            Ore.COAL to 50.0,
            Ore.GOLD to 65.0,
            Ore.MITHRIL to 80.0,
            Ore.ADAMANT to 95.0,
            Ore.RUNE to 125.0,
        )

        assertEquals(expected, Ore.entries.associateWith { it.exp })
    }

    @Test
    fun `Tutorial Island's rocks are copper and tin, and empty into an ordinary empty rock`() {
        assertEquals(listOf(Ore.COPPER, Ore.TIN), listOf(Ore.ROCK_MAP[3042], Ore.ROCK_MAP[3043]))
        assertEquals(listOf(450, 450), listOf(Ore.ORE_TO_EMPTY[3042], Ore.ORE_TO_EMPTY[3043]))
    }
}
