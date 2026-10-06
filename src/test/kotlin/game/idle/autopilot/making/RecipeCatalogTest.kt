package game.idle.autopilot.making

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class RecipeCatalogTest {

    private val dough = """{ "product": 2307, "name": "bread dough", "use": 1933, "on": 1929 }"""

    private fun parse(vararg recipes: String) = RecipeCatalog.parse("""{ "recipes": [${recipes.joinToString(",")}] }""")

    @Test
    fun `parse reads every field of a recipe`() {
        assertEquals(listOf(Recipe(2307, "bread dough", 1933, 1929)), parse(dough).recipes)
    }

    @Test
    fun `a recipe is found by name, an unknown one is not`() {
        val catalog = parse(dough)

        assertEquals(2307, catalog.find("bread dough")?.product)
        assertNull(catalog.find("cake"))
    }

    @Test
    fun `the tracked data file loads with bread dough, an unfinished guam potion and arrow shafts`() {
        val names = RecipeCatalog.load(RecipeCatalog.PATH).recipes.map { it.name }

        assertEquals(listOf("bread dough", "guam potion (unf)", "arrow shaft"), names)
    }

    @Test
    fun `an existing file is read and a missing one is an error`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("recipes.jsonc"), """{ "recipes": [$dough] }""")

        assertEquals(1, RecipeCatalog.load(file).recipes.size)
        assertThrows<NoSuchFileException> { RecipeCatalog.load(dir.resolve("none.jsonc")) }
    }

    @Test
    fun `an empty document gives an empty catalog`() {
        assertEquals(emptyList<Recipe>(), RecipeCatalog.parse("").recipes)
        assertEquals(emptyList<Recipe>(), RecipeCatalog.parse("{}").recipes)
    }

    @Test
    fun `duplicate names are rejected`() {
        assertRejected("Duplicate recipe names: [bread dough]") { parse(dough, dough) }
    }

    @Test
    fun `a recipe needs a lower case name, a product and both items`() {
        assertRejected("A recipe has no name: RecipeJson(product=1, name=, use=2, on=3)") { parse("""{ "product": 1, "use": 2, "on": 3 }""") }
        assertRejected("Recipe 'Bread' must be named in lower case") { parse("""{ "product": 1, "name": "Bread", "use": 2, "on": 3 }""") }
        assertRejected("Recipe 'bread' has no product") { parse("""{ "name": "bread", "use": 2, "on": 3 }""") }
        assertRejected("Recipe 'bread' has no item to use") { parse("""{ "product": 1, "name": "bread", "on": 3 }""") }
        assertRejected("Recipe 'bread' has no item to use it on") { parse("""{ "product": 1, "name": "bread", "use": 2 }""") }
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<IllegalArgumentException> { action() }.message)
    }
}
