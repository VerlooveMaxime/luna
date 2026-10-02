package game.idle.content.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LostCityContentTest {

    private val guardConfig = """
        // Draynor jail
        [jailguard]
        name=Jail guard
        hitpoints=32 // from the knowledge base
        param=attackrate,5
        param=damagetype,^crush_style
    """.trimIndent()

    @Test
    fun `a pack maps ids to debugnames`() {
        assertEquals(mapOf(0 to "hans", 447 to "jailguard"), LostCityFormats.parsePack("0=hans\n447=jailguard\n"))
    }

    @Test
    fun `a config block holds its key and value lines in order`() {
        assertEquals(
            listOf(
                "name" to "Jail guard",
                "hitpoints" to "32",
                "param" to "attackrate,5",
                "param" to "damagetype,^crush_style",
            ),
            LostCityFormats.parseBlocks(guardConfig).single().lines,
        )
    }

    @Test
    fun `each header starts a block`() {
        val blocks = LostCityFormats.parseBlocks("[man]\nname=Man\n\n[woman]\nname=Woman\n")

        assertEquals(listOf("man", "woman"), blocks.map { it.debugname })
    }

    @Test
    fun `lines before the first header belong to no block`() {
        val block = LostCityFormats.parseBlocks("version=2\n[man]\nname=Man\n").single()

        assertEquals(listOf("name" to "Man"), block.lines)
    }

    @Test
    fun `a key's last line gives its value`() {
        val block = LostCityFormats.parseBlocks("[man]\nwanderrange=1\nwanderrange=2\n").single()

        assertEquals("2", block.value("wanderrange"))
    }

    @Test
    fun `a missing key has no value`() {
        assertNull(LostCityFormats.parseBlocks(guardConfig).single().value("attack"))
    }

    @Test
    fun `params map their name to the rest of the line`() {
        assertEquals(
            mapOf("attackrate" to "5", "damagetype" to "^crush_style"),
            LostCityFormats.parseBlocks(guardConfig).single().params,
        )
    }

    private fun checkout(dir: Path) = lostCityTree(
        root = dir,
        npcPack = "447=jailguard\n950=newbiegiantrat\n",
        seqPack = "138=giantrat_attack\n",
        npcFiles = mapOf(
            "_unpack/377/all.npc" to "[newbiegiantrat]\nname=Giant rat\n",
            "areas/area_draynor/configs/jail.npc" to guardConfig,
            "areas/area_draynor/configs/notes.txt" to "[ignored]\nname=Not an npc\n",
        ),
    )

    @Test
    fun `a checkout finds an npc by id through its pack`(@TempDir dir: Path) {
        assertEquals("Jail guard", LostCityBranch.read(checkout(dir)).npc(447)?.value("name"))
    }

    @Test
    fun `a checkout reads npc configs in every directory under scripts`(@TempDir dir: Path) {
        assertEquals("Giant rat", LostCityBranch.read(checkout(dir)).npc(950)?.value("name"))
    }

    @Test
    fun `files that are not npc configs are not read`(@TempDir dir: Path) {
        assertNull(LostCityBranch.read(checkout(dir)).npcNamed("ignored"))
    }

    @Test
    fun `an id the pack lacks has no npc`(@TempDir dir: Path) {
        assertNull(LostCityBranch.read(checkout(dir)).npc(1))
    }

    @Test
    fun `an npc is also found by debugname`(@TempDir dir: Path) {
        assertEquals("Jail guard", LostCityBranch.read(checkout(dir)).npcNamed("jailguard")?.value("name"))
    }

    @Test
    fun `the pack gives each id its debugname`(@TempDir dir: Path) {
        assertEquals("newbiegiantrat", LostCityBranch.read(checkout(dir)).debugname(950))
    }

    @Test
    fun `animations map from debugname to id`(@TempDir dir: Path) {
        assertEquals(mapOf("giantrat_attack" to 138), LostCityBranch.read(checkout(dir)).sequences)
    }

    @Test
    fun `bytes that are not UTF-8 are read as replacement characters`(@TempDir dir: Path) {
        val root = checkout(dir)
        Files.write(root.resolve("scripts/odd.npc"), "[odd]\nname=A".toByteArray() + byteArrayOf(0xFF.toByte()))

        assertEquals("A�", LostCityBranch.read(root).npcNamed("odd")?.value("name"))
    }
}
