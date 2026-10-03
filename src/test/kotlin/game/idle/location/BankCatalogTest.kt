package game.idle.location

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class BankCatalogTest {

    private fun bank(id: String = "draynor", name: String? = "Draynor bank", booth: String? = """{ "x": 3091, "y": 3242 }"""): String {
        val fields = listOfNotNull(
            """"id": "$id"""",
            name?.let { """"name": "$it"""" },
            booth?.let { """"booth": $it""" },
        )
        return "{ ${fields.joinToString(", ")} }"
    }

    private fun parse(vararg banks: String): BankCatalog = BankCatalog.parse("""{ "banks": [${banks.joinToString(",")}] }""")

    @Test
    fun `parse reads every field of a bank`() {
        assertEquals(listOf(Bank("draynor", "Draynor bank", Tile(3091, 3242))), parse(bank()).banks)
    }

    @Test
    fun `a booth floor is read`() {
        assertEquals(Tile(1, 2, 1), parse(bank(booth = """{ "x": 1, "y": 2, "z": 1 }""")).banks.single().booth)
    }

    @Test
    fun `a bank is found by id, an unknown id is not`() {
        val catalog = parse(bank())

        assertEquals("Draynor bank", catalog.find("draynor")?.name)
        assertNull(catalog.find("lumbridge"))
    }

    @Test
    fun `the tracked data file loads and holds the Varrock west booth`() {
        assertEquals(Tile(3186, 3440), BankCatalog.load(BankCatalog.PATH).find("varrock_west")?.booth)
    }

    @Test
    fun `an existing file is read`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("banks.jsonc"), """{ "banks": [${bank(id = "spot")}] }""")

        assertEquals(listOf("spot"), BankCatalog.load(file).banks.map { it.id })
    }

    @Test
    fun `a missing file is an error`(@TempDir dir: Path) {
        assertThrows<NoSuchFileException> { BankCatalog.load(dir.resolve("banks.jsonc")) }
    }

    @Test
    fun `an empty document gives an empty catalog`() {
        assertEquals(emptyList<Bank>(), BankCatalog.parse("").banks)
    }

    @Test
    fun `a document without banks gives an empty catalog`() {
        assertEquals(emptyList<Bank>(), BankCatalog.parse("{}").banks)
    }

    @Test
    fun `duplicate ids are rejected`() {
        assertRejected("Duplicate bank ids: [draynor]") { parse(bank(), bank()) }
    }

    @Test
    fun `a bank needs an id, a name and a booth`() {
        assertRejected("A bank has no id: BankJson(id=, name=Draynor bank, booth=TileJson(x=3091, y=3242, z=0))") { parse(bank(id = "")) }
        assertRejected("Bank 'draynor' has no name") { parse(bank(name = null)) }
        assertRejected("Bank 'draynor' has no booth") { parse(bank(booth = null)) }
    }

    @Test
    fun `a booth tile must lie on the map`() {
        assertRejected("Bank 'draynor' has a negative booth x") { parse(bank(booth = """{ "x": -1, "y": 1 }""")) }
        assertRejected("Bank 'draynor' has a negative booth y") { parse(bank(booth = """{ "x": 1, "y": -1 }""")) }
        assertRejected("Bank 'draynor' has booth floor 4, expected 0 to 3") { parse(bank(booth = """{ "x": 1, "y": 1, "z": 4 }""")) }
        assertRejected("Bank 'draynor' has booth floor -1, expected 0 to 3") { parse(bank(booth = """{ "x": 1, "y": 1, "z": -1 }""")) }
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        val error = assertThrows<IllegalArgumentException> { action() }

        assertEquals(message, error.message)
    }
}
