package game.idle.autopilot.fighting

import game.idle.content.audit.NpcKind
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FightTargetCatalogTest {

    private fun npc(id: Int, name: String, level: Int, attack: String = "Attack") =
        NpcKind(id, name, level, listOf("Talk-to", attack, "", "", ""))

    @Test
    fun `npcs of one name are one target, spelled as the cache spells the first`() {
        val catalog = FightTargetCatalog.of(listOf(npc(81, "Cow", 2), npc(397, "cow", 2)))

        assertEquals(listOf(FightTarget("cow", setOf(81, 397), "Cow", 2..2)), catalog.targets)
    }

    @Test
    fun `a target spans the combat levels of its npcs`() {
        val catalog = FightTargetCatalog.of(listOf(npc(86, "Giant rat", 3), npc(87, "Giant rat", 6), npc(446, "Giant rat", 1)))

        assertEquals(1..6, catalog.targets.single().levels)
    }

    @Test
    fun `an npc without an attack option is no target`() {
        val catalog = FightTargetCatalog.of(listOf(npc(0, "Hans", 0, attack = "")))

        assertEquals(emptyList<FightTarget>(), catalog.targets)
    }

    @Test
    fun `an npc at combat level 0 is no target`() {
        val catalog = FightTargetCatalog.of(listOf(npc(1532, "Barricade", 0)))

        assertEquals(emptyList<FightTarget>(), catalog.targets)
    }

    @Test
    fun `a name keeps its npcs above level 0`() {
        val catalog = FightTargetCatalog.of(listOf(npc(2, "Rat", 0), npc(47, "Rat", 1)))

        assertEquals(listOf(FightTarget("rat", setOf(47), "Rat", 1..1)), catalog.targets)
    }

    @Test
    fun `a target is found by its lower-case name`() {
        val catalog = FightTargetCatalog.of(listOf(npc(81, "Cow", 2)))

        assertEquals(setOf(81), catalog.find("cow")?.npcs)
    }

    @Test
    fun `an unknown name finds no target`() {
        assertNull(FightTargetCatalog.of(listOf(npc(81, "Cow", 2))).find("goblin"))
    }

    @Test
    fun `the cache's catalog holds the island rat with the mainland's giant rats`() {
        TestWorld.context

        val rats = FightTargetCatalog.fromCache().find("giant rat")

        assertEquals(listOf(true, true, "Giant rat"), listOf(rats?.npcs?.contains(950), rats?.npcs?.contains(86), rats?.label))
    }

    @Test
    fun `the cache's catalog leaves out npcs nobody can attack`() {
        TestWorld.context

        assertNull(FightTargetCatalog.fromCache().find("hans"))
    }

    @Test
    fun `the cache's catalog leaves out level-0 minigame pieces and keeps the levels of mixed names`() {
        TestWorld.context
        val catalog = FightTargetCatalog.fromCache()

        assertEquals(listOf(null, 84..103), listOf(catalog.find("barricade"), catalog.find("mummy")?.levels))
    }
}
