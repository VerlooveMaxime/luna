package game.idle.content.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContentAuditTest {

    private val goblin = npc(100, "", "Attack", name = "Goblin", level = 2)
    private val hans = npc(0, "Talk-to", name = "Hans")
    private val door = obj(1530, "Open", name = "Door")

    @Test
    fun `an attackable npc without a combat definition is reported with its count`() {
        assertEquals(listOf(Shortfall(goblin, 3)), area(spawned(goblin, count = 3)).withoutCombatDefinition)
    }

    @Test
    fun `an attackable npc with a combat definition is not reported`() {
        val facts = spawned(goblin).copy(combatDefinitions = mapOf(goblin.id to stats()))

        assertEquals(emptyList<Shortfall>(), area(facts).withoutCombatDefinition)
    }

    @Test
    fun `a max hit under half of what strength gives is a placeholder`() {
        val placeholder = stats(maximumHit = 1, strength = 23)
        val facts = spawned(goblin, count = 2).copy(combatDefinitions = mapOf(goblin.id to placeholder))

        assertEquals(listOf(PlaceholderCombat(Shortfall(goblin, 2), placeholder)), area(facts).placeholderCombat)
    }

    @Test
    fun `a max hit of half what strength gives is believable`() {
        val facts = spawned(goblin).copy(combatDefinitions = mapOf(goblin.id to stats(maximumHit = 2, strength = 27)))

        assertEquals(emptyList<PlaceholderCombat>(), area(facts).placeholderCombat)
    }

    @Test
    fun `a combat definition with every skill 0 is a placeholder`() {
        val unfilled = stats(maximumHit = 1, strength = 0, otherSkills = 0)
        val facts = spawned(goblin).copy(combatDefinitions = mapOf(goblin.id to unfilled))

        assertEquals(listOf(unfilled), area(facts).placeholderCombat.map { it.stats })
    }

    @Test
    fun `an npc without a combat definition is not counted as a placeholder`() {
        assertEquals(emptyList<PlaceholderCombat>(), area(spawned(goblin)).placeholderCombat)
    }

    @Test
    fun `placeholders are listed most numerous first`() {
        val imp = npc(708, "", "Attack", name = "Imp")
        val facts = facts(
            npcs = listOf(imp, goblin),
            spawns = listOf(RegionCount(DRAYNOR, imp.id, 1), RegionCount(DRAYNOR, goblin.id, 5)),
            combatDefinitions = mapOf(imp.id to stats(maximumHit = 1), goblin.id to stats(maximumHit = 1)),
        )

        assertEquals(listOf(goblin, imp), area(facts).placeholderCombat.map { it.shortfall.kind })
    }

    @Test
    fun `an npc that cannot be attacked needs no combat definition`() {
        assertEquals(emptyList<Shortfall>(), area(spawned(hans)).withoutCombatDefinition)
    }

    @Test
    fun `an attackable npc without a drop table is reported`() {
        assertEquals(listOf(Shortfall(goblin, 1)), area(spawned(goblin)).withoutDropTable)
    }

    @Test
    fun `an attackable npc with a drop table is not reported`() {
        val facts = spawned(goblin).copy(dropTableIds = setOf(goblin.id))

        assertEquals(emptyList<Shortfall>(), area(facts).withoutDropTable)
    }

    @Test
    fun `an npc option without a handler is reported under its client number`() {
        assertEquals(
            mapOf(MenuOption(1, "Talk-to") to listOf(Shortfall(hans, 1))),
            area(spawned(hans)).unhandledNpcOptions,
        )
    }

    @Test
    fun `an npc option with a handler is not reported`() {
        val facts = spawned(hans).copy(npcHandlers = mapOf(0 to setOf(hans.id)))

        assertEquals(emptyMap<MenuOption, List<Shortfall>>(), area(facts).unhandledNpcOptions)
    }

    @Test
    fun `a handler for another option does not count`() {
        val facts = spawned(hans).copy(npcHandlers = mapOf(2 to setOf(hans.id)))

        assertEquals(listOf(MenuOption(1, "Talk-to")), area(facts).unhandledNpcOptions.keys.toList())
    }

    @Test
    fun `the attack option is left to the combat rules`() {
        assertEquals(emptyMap<MenuOption, List<Shortfall>>(), area(spawned(goblin)).unhandledNpcOptions)
    }

    @Test
    fun `another label on the attack option reaches no handler`() {
        val sheep = npc(43, "", "Shear", name = "Sheep")

        assertEquals(listOf(MenuOption(2, "Shear")), area(spawned(sheep)).unhandledNpcOptions.keys.toList())
    }

    @Test
    fun `an object option without a handler is reported`() {
        assertEquals(
            mapOf(MenuOption(1, "Open") to listOf(Shortfall(door, 4))),
            area(placed(door, count = 4)).unhandledObjectOptions,
        )
    }

    @Test
    fun `an object option with a handler is not reported`() {
        val facts = placed(door).copy(objectHandlers = mapOf(0 to setOf(door.id)))

        assertEquals(emptyMap<MenuOption, List<Shortfall>>(), area(facts).unhandledObjectOptions)
    }

    @Test
    fun `an object option labelled Attack is still an object option`() {
        val target = obj(2000, "Attack", name = "Target")

        assertEquals(listOf(MenuOption(1, "Attack")), area(placed(target)).unhandledObjectOptions.keys.toList())
    }

    @Test
    fun `the regions of one zone form one area`() {
        val facts = facts(
            npcs = listOf(hans),
            spawns = listOf(RegionCount(LUMBRIDGE_EAST, hans.id, 1), RegionCount(LUMBRIDGE_WEST, hans.id, 2)),
        )

        assertEquals(
            listOf("lumbridge" to listOf(LUMBRIDGE_WEST, LUMBRIDGE_EAST)),
            audit(facts).map { it.name to it.regions },
        )
    }

    @Test
    fun `counts of one kind add up over the regions of a zone`() {
        val facts = facts(
            npcs = listOf(goblin),
            spawns = listOf(RegionCount(LUMBRIDGE_EAST, goblin.id, 1), RegionCount(LUMBRIDGE_WEST, goblin.id, 2)),
        )

        assertEquals(listOf(Shortfall(goblin, 3)), area(facts).withoutCombatDefinition)
    }

    @Test
    fun `a region outside every zone is an area of its own`() {
        val area = area(spawned(hans, region = UNZONED_SOUTH))

        assertEquals(listOf("region $UNZONED_SOUTH", false), listOf(area.name, area.zone))
    }

    @Test
    fun `zones come first by name, then the other regions by id`() {
        val facts = facts(
            npcs = listOf(hans),
            spawns = listOf(UNZONED_NORTH, LUMBRIDGE_EAST, UNZONED_SOUTH, DRAYNOR).map { RegionCount(it, hans.id, 1) },
        )

        assertEquals(
            listOf("draynor", "lumbridge", "region $UNZONED_SOUTH", "region $UNZONED_NORTH"),
            audit(facts).map { it.name },
        )
    }

    @Test
    fun `an area counts its npcs, its objects and their kinds`() {
        val facts = facts(
            npcs = listOf(hans, goblin),
            objects = listOf(door),
            spawns = listOf(RegionCount(LUMBRIDGE_EAST, hans.id, 1), RegionCount(LUMBRIDGE_EAST, goblin.id, 4)),
            placements = listOf(RegionCount(LUMBRIDGE_EAST, door.id, 7)),
        )
        val area = area(facts)

        assertEquals(
            listOf(5, 2, 7, 1),
            listOf(area.npcCount, area.npcKindCount, area.objectCount, area.objectKindCount),
        )
    }

    @Test
    fun `npcs and objects of other areas are not counted`() {
        val facts = facts(
            npcs = listOf(hans),
            objects = listOf(door),
            spawns = listOf(RegionCount(DRAYNOR, hans.id, 1)),
            placements = listOf(RegionCount(LUMBRIDGE_EAST, door.id, 7)),
        )

        assertEquals(listOf("draynor" to 1, "lumbridge" to 0), audit(facts).map { it.name to it.npcCount })
    }

    @Test
    fun `kinds are listed most numerous first, ties by id`() {
        val imp = npc(708, "", "Attack", name = "Imp")
        val man = npc(1, "", "Attack", name = "Man")
        val facts = facts(
            npcs = listOf(imp, goblin, man),
            spawns = listOf(
                RegionCount(DRAYNOR, imp.id, 1),
                RegionCount(DRAYNOR, goblin.id, 5),
                RegionCount(DRAYNOR, man.id, 1),
            ),
        )

        assertEquals(listOf(goblin, man, imp), area(facts).withoutCombatDefinition.map { it.kind })
    }

    @Test
    fun `options are listed by how many things they leave unhandled`() {
        val crate = obj(355, "Search", name = "Crate")
        val facts = facts(
            objects = listOf(crate, door),
            placements = listOf(RegionCount(DRAYNOR, crate.id, 2), RegionCount(DRAYNOR, door.id, 9)),
        )

        assertEquals(listOf("Open", "Search"), area(facts).unhandledObjectOptions.keys.map { it.label })
    }

    @Test
    fun `options leaving as many things unhandled are ordered by number, then label`() {
        val crate = obj(355, "Search", name = "Crate")
        val gate = obj(1551, "", "Close", name = "Gate")
        val facts = facts(
            objects = listOf(gate, crate, door),
            placements = listOf(crate, gate, door).map { RegionCount(DRAYNOR, it.id, 1) },
        )

        assertEquals(
            listOf(MenuOption(1, "Open"), MenuOption(1, "Search"), MenuOption(2, "Close")),
            area(facts).unhandledObjectOptions.keys.toList(),
        )
    }

    @Test
    fun `the finding count adds up all five rules`() {
        val imp = npc(708, "", "Attack", name = "Imp")
        val facts = facts(
            npcs = listOf(goblin, imp, hans),
            objects = listOf(door),
            spawns = listOf(goblin, imp, hans).map { RegionCount(LUMBRIDGE_EAST, it.id, 1) },
            placements = listOf(RegionCount(LUMBRIDGE_EAST, door.id, 1)),
            combatDefinitions = mapOf(imp.id to stats(maximumHit = 1)),
        )

        assertEquals(6, area(facts).findingCount)
    }
}
