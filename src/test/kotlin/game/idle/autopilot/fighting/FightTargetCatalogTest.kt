package game.idle.autopilot.fighting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class FightTargetCatalogTest {

    private val chicken = """{ "name": "chicken", "npcs": [41, 951] }"""

    private fun parse(vararg targets: String) = FightTargetCatalog.parse("""{ "targets": [${targets.joinToString(",")}] }""")

    @Test
    fun `parse reads the name and npcs of a target`() {
        assertEquals(listOf(FightTarget("chicken", setOf(41, 951))), parse(chicken).targets)
    }

    @Test
    fun `a target is found by name`() {
        assertEquals(setOf(41, 951), parse(chicken).find("chicken")?.npcs)
    }

    @Test
    fun `an unknown name finds no target`() {
        assertNull(parse(chicken).find("cow"))
    }

    @Test
    fun `the tracked data file loads chickens, cows and giant rats`() {
        val names = FightTargetCatalog.load(FightTargetCatalog.PATH).targets.map { it.name }

        assertEquals(listOf("chicken", "cow", "giant rat"), names)
    }

    @Test
    fun `an existing file is read`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("fight_targets.jsonc"), """{ "targets": [$chicken] }""")

        assertEquals(1, FightTargetCatalog.load(file).targets.size)
    }

    @Test
    fun `a missing file is an error`(@TempDir dir: Path) {
        assertThrows<NoSuchFileException> { FightTargetCatalog.load(dir.resolve("none.jsonc")) }
    }

    @Test
    fun `an empty document gives an empty catalog`() {
        assertEquals(emptyList<FightTarget>(), FightTargetCatalog.parse("").targets)
    }

    @Test
    fun `a document without targets gives an empty catalog`() {
        assertEquals(emptyList<FightTarget>(), FightTargetCatalog.parse("{}").targets)
    }

    @Test
    fun `duplicate names are rejected`() {
        assertRejected("Duplicate fight target names: [chicken]") { parse(chicken, chicken) }
    }

    @Test
    fun `a target needs a name`() {
        assertRejected("A fight target has no name: FightTargetJson(name=, npcs=[1])") { parse("""{ "npcs": [1] }""") }
    }

    @Test
    fun `a target is named in lower case`() {
        assertRejected("Fight target 'Cow' must be named in lower case") { parse("""{ "name": "Cow", "npcs": [81] }""") }
    }

    @Test
    fun `a target needs npcs`() {
        assertRejected("Fight target 'cow' has no npcs") { parse("""{ "name": "cow" }""") }
    }

    private fun assertRejected(message: String, block: () -> Unit) {
        assertEquals(message, assertThrows<IllegalArgumentException>(block).message)
    }
}
