package game.skill.crafting.armorCrafting

import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CraftStuddedActionItemTest {

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun name(id: Int): String = ItemDefinition.ALL.retrieve(id).name

    @Test
    fun `studs on a leather body or leather chaps make the studded one`() {
        val pairs = CraftStuddedActionItem.LEATHER_TO_STUDDED.map { (leather, studded) -> name(leather.id) to name(studded.id) }

        assertEquals(listOf("Leather body" to "Studded body", "Leather chaps" to "Studded chaps"), pairs)
    }
}
