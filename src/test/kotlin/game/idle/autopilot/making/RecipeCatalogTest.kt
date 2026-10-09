package game.idle.autopilot.making

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RecipeCatalogTest {

    @Test
    fun `a recipe is called by its label in lower case`() {
        assertEquals("bread dough", BREAD_DOUGH.name)
    }

    @Test
    fun `a recipe is found by name, an unknown one is not`() {
        val catalog = RecipeCatalog(listOf(BREAD_DOUGH))

        assertEquals(BREAD_DOUGH, catalog.find("bread dough"))
        assertNull(catalog.find("cake"))
    }

    @Test
    fun `two recipes with one name are refused`() {
        val error = assertThrows<IllegalArgumentException> { RecipeCatalog(listOf(BREAD_DOUGH, BREAD_DOUGH.copy(product = 1863))) }

        assertEquals("Duplicate recipe names: [bread dough]", error.message)
    }
}
