package game.idle.content.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Paths

class NpcCombatFileTest {

    private val file = NpcCombatFile.parse(COMBAT_FILE_TEXT)

    @Test
    fun `every row of the file is read`() {
        assertEquals(listOf(1, 447), file.rows.map { it.id })
    }

    @Test
    fun `a row reads every field and the name its id line carries`() {
        assertEquals(row(), file.row(1))
    }

    @Test
    fun `an aggression object is read with its tolerance`() {
        assertEquals(COMBAT_LEVEL_TEN, file.row(447)?.aggression)
    }

    @Test
    fun `poison flags are read`() {
        assertEquals(listOf(true, true), listOf(file.row(447)?.poisonous, file.row(447)?.immunePoison))
    }

    @Test
    fun `an id the file lacks has no row`() {
        assertNull(file.row(2))
    }

    @Test
    fun `an id line without a comment reads an empty name`() {
        val text = COMBAT_FILE_HEAD + MAN_BLOCK.replace(" // Man", "") + "\n]\n"

        assertEquals("", NpcCombatFile.parse(text).row(1)?.comment)
    }

    @Test
    fun `a row renders in the file's layout`() {
        assertEquals(MAN_BLOCK, NpcCombatFile.render(row()))
    }

    @Test
    fun `an aggression object renders in the file's layout`() {
        assertEquals(GUARD_BLOCK, file.row(447)?.let(NpcCombatFile::render))
    }

    @Test
    fun `a row without a name renders an id line without a comment`() {
        assertEquals("    \"id\": 1,", NpcCombatFile.render(row(comment = "")).lines()[1])
    }

    @Test
    fun `replacing a row changes only that row`() {
        val text = file.withRows(listOf(row(hitpoints = 10)))

        assertEquals(COMBAT_FILE_TEXT.replace("\"hitpoints\": 7,", "\"hitpoints\": 10,"), text)
    }

    @Test
    fun `replacing no row keeps the file`() {
        assertEquals(COMBAT_FILE_TEXT, file.withRows(emptyList()))
    }

    @Test
    fun `a row the file does not have is refused`() {
        assertThrows<IllegalArgumentException> { file.withRows(listOf(row(id = 2))) }
    }

    @Test
    fun `every row of Luna's npc_combat jsonc renders back to the same text`() {
        val text = Files.readString(Paths.get("data", "game", "def", "npcs", "npc_combat.jsonc"))
        val real = NpcCombatFile.parse(text)

        assertEquals(text, real.withRows(real.rows))
    }
}
