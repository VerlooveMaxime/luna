package game.idle.location

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class LocationCatalogTest {

    @Test
    fun `parse reads every field of a location`() {
        val location = parseOne()

        val expected = Location(
            id = "varrock_west",
            name = "West of Varrock",
            anchor = Tile(3165, 3445),
            radius = 15,
            trees = listOf("normal"),
            bank = null,
            unlock = LocationUnlock(stage = 0),
        )
        assertEquals(expected, location)
    }

    @Test
    fun `a bank tile is read`() {
        assertEquals(Tile(3186, 3440), parseOne("bank" to mapOf("x" to 3186, "y" to 3440)).bank)
    }

    @Test
    fun `the tracked Varrock location has its west bank booth`() {
        assertEquals(Tile(3186, 3440), LocationCatalog.load(LocationCatalog.PATH).find("varrock_west")?.bank)
    }

    @Test
    fun `a bank tile is checked like the anchor`() {
        assertRejected("Location 'spot' has a negative bank x", "id" to "spot", "bank" to mapOf("x" to -1, "y" to 1))
        assertRejected("Location 'spot' has a negative bank y", "id" to "spot", "bank" to mapOf("x" to 1, "y" to -1))
        assertRejected("Location 'spot' has bank floor 4, expected 0 to 3", "id" to "spot", "bank" to mapOf("x" to 1, "y" to 1, "z" to 4))
    }

    @Test
    fun `the tracked data file loads and holds the Varrock trees`() {
        val catalog = LocationCatalog.load(LocationCatalog.PATH)

        assertEquals("West of Varrock", catalog.find("varrock_west")?.name)
    }

    @Test
    fun `an existing file is read`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("locations.jsonc"), catalogJson(locationJson("id" to "spot")))

        val catalog = LocationCatalog.load(file)

        assertEquals(listOf("spot"), catalog.locations.map { it.id })
    }

    @Test
    fun `a missing file is an error`(@TempDir dir: Path) {
        assertThrows<NoSuchFileException> { LocationCatalog.load(dir.resolve("locations.jsonc")) }
    }

    @Test
    fun `an empty document gives an empty catalog`() {
        assertEquals(0, LocationCatalog.parse("").size)
    }

    @Test
    fun `a document without locations gives an empty catalog`() {
        assertEquals(0, LocationCatalog.parse("{}").size)
    }

    @Test
    fun `comments are ignored`() {
        val jsonc = """
            {
              // the list
              "locations": [${locationJson()}]
            }
        """

        assertEquals(1, LocationCatalog.parse(jsonc).size)
    }

    @Test
    fun `duplicate ids are rejected`() {
        val jsonc = catalogJson(locationJson("id" to "twice"), locationJson("id" to "twice"))

        val error = assertThrows<IllegalArgumentException> { LocationCatalog.parse(jsonc) }

        assertEquals("Duplicate location ids: [twice]", error.message)
    }

    @Test
    fun `find gives null for an unknown id`() {
        val catalog = LocationCatalog.parse(catalogJson(locationJson()))

        assertNull(catalog.find("nope"))
    }

    @Test
    fun `an anchor defaults to the ground floor`() {
        assertEquals(0, parseOne("anchor" to mapOf("x" to 1, "y" to 2)).anchor.z)
    }

    @Test
    fun `an upstairs anchor keeps its floor`() {
        assertEquals(Tile(1, 2, 1), parseOne("anchor" to mapOf("x" to 1, "y" to 2, "z" to 1)).anchor)
    }

    @Test
    fun `a tile converts to a position`() {
        val position = Tile(3165, 3445, 1).toPosition()

        assertEquals(listOf(3165, 3445, 1), listOf(position.x, position.y, position.z))
    }

    @Test
    fun `unlock defaults to stage zero`() {
        assertEquals(LocationUnlock(0), parseOne("unlock" to null).unlock)
    }

    @Test
    fun `a missing id is rejected`() {
        val error = assertThrows<IllegalArgumentException> { parseOne("id" to null) }

        assertTrue(error.message.orEmpty().startsWith("A location has no id"), error.message)
    }

    @Test
    fun `a missing name is rejected`() {
        assertRejected("Location 'spot' has no name", "id" to "spot", "name" to null)
    }

    @Test
    fun `a missing anchor is rejected`() {
        assertRejected("Location 'spot' has no anchor", "id" to "spot", "anchor" to null)
    }

    @Test
    fun `a negative anchor x is rejected`() {
        assertRejected("Location 'spot' has a negative anchor x", "id" to "spot", "anchor" to mapOf("x" to -1, "y" to 1))
    }

    @Test
    fun `a negative anchor y is rejected`() {
        assertRejected("Location 'spot' has a negative anchor y", "id" to "spot", "anchor" to mapOf("x" to 1, "y" to -1))
    }

    @Test
    fun `an anchor below the ground floor is rejected`() {
        assertRejected(
            "Location 'spot' has anchor floor -1, expected 0 to 3",
            "id" to "spot",
            "anchor" to mapOf("x" to 1, "y" to 1, "z" to -1),
        )
    }

    @Test
    fun `an anchor above the top floor is rejected`() {
        assertRejected(
            "Location 'spot' has anchor floor 4, expected 0 to 3",
            "id" to "spot",
            "anchor" to mapOf("x" to 1, "y" to 1, "z" to 4),
        )
    }

    @Test
    fun `a radius of zero is rejected`() {
        assertRejected("Location 'spot' has radius 0, expected 1 to 32", "id" to "spot", "radius" to 0)
    }

    @Test
    fun `a radius past the maximum is rejected`() {
        assertRejected("Location 'spot' has radius 33, expected 1 to 32", "id" to "spot", "radius" to 33)
    }

    @Test
    fun `a location with nothing to do is rejected`() {
        assertRejected("Location 'spot' has nothing to do: no trees", "id" to "spot", "trees" to null)
    }

    @Test
    fun `a negative stage is rejected`() {
        assertRejected(
            "Location 'spot' unlocks at stage -1, expected 0 or more",
            "id" to "spot",
            "unlock" to mapOf("stage" to -1),
        )
    }

    private fun assertRejected(message: String, vararg overrides: Pair<String, Any?>) {
        val error = assertThrows<IllegalArgumentException> { parseOne(*overrides) }

        assertEquals(message, error.message)
    }
}
