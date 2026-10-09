package game.skill.fletching.stringBow

import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** IdleRS fixed the willow bows, which Luna strung into each other. */
class BowTest {

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun name(id: Int): String = ItemDefinition.ALL.retrieve(id).name

    @Test
    fun `every bow strings into the bow its unstrung item is named after`() {
        val bows = Bow.VALUES.filter { it != Bow.ARROW_SHAFT }

        assertEquals(bows.map { "${name(it.strung)} (u)" }, bows.map { name(it.unstrung) })
    }
}
