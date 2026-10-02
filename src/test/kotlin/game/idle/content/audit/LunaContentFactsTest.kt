package game.idle.content.audit

import api.bot.zone.Zone
import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LunaContentFactsTest {

    private val man = 1
    private val hans = 0
    private val fungi = 3344
    private val tree = 1276
    private val door = 1530
    private val nameless = 1902
    private val lumbridge = Position(3200, 3200)
    private val draynor = Position(3090, 3245)

    /** Registers [combat] stats and [drops] npc ids, and one handler per option for npc 1 and object 1530. */
    private class FixedRegistries(
        private val combat: Map<Int, CombatStats> = emptyMap(),
        private val drops: Set<Int> = emptySet(),
    ) : ContentRegistries {
        override fun npcHandlers() = mapOf(0 to setOf(1))
        override fun objectHandlers() = mapOf(0 to setOf(1530))
        override fun combatStats(npcId: Int) = combat[npcId]
        override fun hasDropTable(npcId: Int) = npcId in drops
    }

    private fun collect(registries: ContentRegistries = FixedRegistries()) =
        LunaContentFacts(TestWorld.world, registries).collect()

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `spawned npcs are counted per region and id`() {
        TestWorld.spawnNpc(man, lumbridge)
        TestWorld.spawnNpc(man, lumbridge.translate(1, 0))
        TestWorld.spawnNpc(man, draynor)

        assertEquals(
            setOf(RegionCount(lumbridge.regionId, man, 2), RegionCount(draynor.regionId, man, 1)),
            collect().spawns.toSet(),
        )
    }

    @Test
    fun `an npc kind is read from its definition, absent options left empty`() {
        TestWorld.spawnNpc(man, lumbridge)

        assertEquals(NpcKind(man, "Man", 2, listOf("Talk-to", "Attack", "Pickpocket", "", "")), collect().npcs[man])
    }

    @Test
    fun `an object kind is read from its definition, hidden options left empty`() {
        TestWorld.place(tree, lumbridge)

        assertEquals(ObjectKind(tree, "Tree", listOf("Chop down", "", "", "", "")), collect().objects[tree])
    }

    @Test
    fun `a hidden option is left empty whatever its case`() {
        TestWorld.spawnNpc(fungi, lumbridge)

        assertEquals("", collect().npcs.getValue(fungi).actions[2])
    }

    @Test
    fun `placed objects are counted per region and id`() {
        TestWorld.place(door, lumbridge)
        TestWorld.place(door, draynor)

        assertEquals(
            setOf(RegionCount(lumbridge.regionId, door, 1), RegionCount(draynor.regionId, door, 1)),
            collect().placements.toSet(),
        )
    }

    @Test
    fun `objects without a menu option are left out`() {
        TestWorld.place(nameless, lumbridge)
        val facts = collect()

        assertEquals(listOf(0, 0), listOf(facts.objects.size, facts.placements.size))
    }

    @Test
    fun `combat stats are asked for every spawned kind`() {
        TestWorld.spawnNpc(man, lumbridge)
        TestWorld.spawnNpc(hans, lumbridge)
        val registries = FixedRegistries(combat = mapOf(man to stats(), 41 to stats()))

        assertEquals(mapOf(man to stats()), collect(registries).combatDefinitions)
    }

    @Test
    fun `drop tables are asked for every spawned kind`() {
        TestWorld.spawnNpc(man, lumbridge)
        TestWorld.spawnNpc(hans, lumbridge)

        assertEquals(setOf(hans), collect(FixedRegistries(drops = setOf(hans))).dropTableIds)
    }

    @Test
    fun `click handlers come from the registries`() {
        val facts = collect()

        assertEquals(
            listOf(mapOf(0 to setOf(1)), mapOf(0 to setOf(1530))),
            listOf(facts.npcHandlers, facts.objectHandlers),
        )
    }

    @Test
    fun `a region maps to the lower-case name of its zone`() {
        assertEquals("draynor", zoneOfRegion(Zone.entries)[draynor.regionId])
    }
}
