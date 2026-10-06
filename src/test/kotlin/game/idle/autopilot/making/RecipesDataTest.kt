package game.idle.autopilot.making

import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The tracked recipes against the cache's item definitions; skipped where the cache is absent. */
class RecipesDataTest {

    private fun name(id: Int): String {
        TestWorld.world
        return ItemDefinition.ALL.retrieve(id).name.lowercase()
    }

    @Test
    fun `every recipe makes the item it is named after, unfinished potions sharing one name`() {
        val recipes = RecipeCatalog.load(RecipeCatalog.PATH).recipes

        assertEquals(listOf("bread dough", "unfinished potion", "arrow shaft"), recipes.map { name(it.product) })
    }

    @Test
    fun `every recipe combines two real items`() {
        val recipes = RecipeCatalog.load(RecipeCatalog.PATH).recipes

        assertEquals(
            listOf("pot of flour on bucket of water", "vial of water on guam leaf", "knife on logs"),
            recipes.map { "${name(it.use)} on ${name(it.on)}" },
        )
    }
}
