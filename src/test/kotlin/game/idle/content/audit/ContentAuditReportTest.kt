package game.idle.content.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ContentAuditReportTest {

    private val date = LocalDate.of(2026, 10, 2)
    private val goblin = npc(100, "", "Attack", name = "Goblin", level = 2)
    private val hans = npc(0, "Talk-to", name = "Hans")
    private val door = obj(1530, "Open", name = "Door")
    private val largeDoor = obj(1516, "Open", name = "Large door")
    private val jailGuard = npc(447, "", "Attack", name = "Jail guard", level = 26)

    private fun areaAudit(
        name: String = "lumbridge",
        zone: Boolean = true,
        regions: List<Int> = listOf(LUMBRIDGE_WEST, LUMBRIDGE_EAST),
        npcCount: Int = 0,
        objectCount: Int = 0,
        withoutCombatDefinition: List<Shortfall> = emptyList(),
        placeholderCombat: List<PlaceholderCombat> = emptyList(),
        withoutDropTable: List<Shortfall> = emptyList(),
        unhandledNpcOptions: Map<MenuOption, List<Shortfall>> = emptyMap(),
        unhandledObjectOptions: Map<MenuOption, List<Shortfall>> = emptyMap(),
    ) = AreaAudit(
        name, zone, regions, npcCount, npcKindCount = 1, objectCount, objectKindCount = 2,
        withoutCombatDefinition, placeholderCombat, withoutDropTable, unhandledNpcOptions, unhandledObjectOptions,
    )

    private val lumbridge = areaAudit(
        npcCount = 4,
        objectCount = 6,
        withoutCombatDefinition = listOf(Shortfall(goblin, 3)),
        placeholderCombat = listOf(PlaceholderCombat(Shortfall(jailGuard, 2), stats(maximumHit = 1))),
        withoutDropTable = listOf(Shortfall(goblin, 3)),
        unhandledNpcOptions = mapOf(MenuOption(1, "Talk-to") to listOf(Shortfall(hans, 1))),
        unhandledObjectOptions = mapOf(MenuOption(1, "Open") to listOf(Shortfall(door, 4), Shortfall(largeDoor, 2))),
    )

    private fun unzoned(region: Int, findings: List<Shortfall> = emptyList()) = areaAudit(
        name = "region $region",
        zone = false,
        regions = listOf(region),
        npcCount = 1,
        withoutCombatDefinition = findings,
    )

    private fun files(vararg areas: AreaAudit) = ContentAuditReport(areas.toList(), date).files()

    private fun overviewRows(vararg areas: AreaAudit): List<List<String>> =
        files(*areas).getValue(ContentAuditReport.OVERVIEW).trimEnd().lines().drop(14).map { it.split(COLUMN_GAP) }

    @Test
    fun `the files are the overview, one per zone, then the unzoned regions`() {
        val files = files(areaAudit(name = "lumbridge"), areaAudit(name = "draynor"), unzoned(UNZONED_SOUTH))

        assertEquals(listOf("overview.txt", "lumbridge.txt", "draynor.txt", "unzoned.txt"), files.keys.toList())
    }

    @Test
    fun `a zone file lists every rule`() {
        val expected = """
            Content audit: lumbridge, 2026-10-02
            Regions: 12849, 12850
            Spawned npcs: 4 of 1 kinds. Objects with a menu option: 6 of 2 kinds.

            Attackable npcs without a combat definition (Luna fights them with Man's stats):
              x3    Goblin (100, level 2)
            Attackable npcs with a placeholder combat definition (max hit under half of what their strength gives, or every skill 0):
              x2    Jail guard (447, level 26): max hit 1, strength 23 gives 3
            Attackable npcs without a drop table:
              x3    Goblin (100, level 2)
            Npc options that reach no handler:
              Talk-to (option 1): Hans (0) x1
            Object options that reach no handler (the server never receives options 4 and 5):
              Open (option 1): Door (1530) x4, Large door (1516) x2

        """.trimIndent()

        assertEquals(expected, files(lumbridge).getValue("lumbridge.txt"))
    }

    @Test
    fun `a placeholder with every skill 0 says so`() {
        val unfilled = PlaceholderCombat(Shortfall(goblin, 1), stats(maximumHit = 1, strength = 0, otherSkills = 0))

        assertEquals(
            "  x1    Goblin (100, level 2): every skill 0, max hit 1",
            files(areaAudit(placeholderCombat = listOf(unfilled))).getValue("lumbridge.txt").lines()[7],
        )
    }

    @Test
    fun `a rule that finds nothing says none`() {
        val lines = files(areaAudit()).getValue("lumbridge.txt").lines()

        assertEquals(listOf("Attackable npcs without a drop table:", "  none"), lines.subList(8, 10))
    }

    private fun patchOptionLine(label: String): String {
        val patch = obj(8391, "Rake", label, name = "Tree patch")
        val options = mapOf(MenuOption(2, label) to listOf(Shortfall(patch, 1)))
        return files(areaAudit(unhandledObjectOptions = options)).getValue("lumbridge.txt").lines()[13]
    }

    @Test
    fun `an option label holding a control character is shown as unreadable`() {
        assertEquals(
            "  unreadable label of 3 characters (option 2): Tree patch (8391) x1",
            patchOptionLine("!\u0009!"),
        )
    }

    @Test
    fun `an option label holding a character beyond ASCII is shown as unreadable`() {
        assertEquals(
            "  unreadable label of 2 characters (option 2): Tree patch (8391) x1",
            patchOptionLine("\uFFCB!"),
        )
    }

    @Test
    fun `the unzoned file lists only the regions that lack something`() {
        val files = files(unzoned(UNZONED_SOUTH), unzoned(UNZONED_NORTH, listOf(Shortfall(goblin, 1))))

        assertEquals(
            listOf(
                "Content audit: regions outside every zone, 2026-10-02",
                "2 regions hold npcs or objects with a menu option; the 1 that lack something are listed by id.",
                "",
                "== region 12337: x 3072-3135, y 3136-3199 ==",
                "Spawned npcs: 1 of 1 kinds. Objects with a menu option: 0 of 2 kinds.",
            ),
            files.getValue("unzoned.txt").lines().take(5),
        )
    }

    @Test
    fun `a region title spans its 64 tiles on both axes`() {
        assertEquals("region 12850: x 3200-3263, y 3200-3263", ContentAuditReport.regionTitle(LUMBRIDGE_EAST))
    }

    @Test
    fun `the overview counts each kind lacking something once, world-wide`() {
        val files = files(lumbridge, unzoned(UNZONED_SOUTH, listOf(Shortfall(goblin, 1))))

        assertEquals(
            listOf(
                "Kinds lacking something, world-wide:",
                "  1 attackable kinds without a combat definition (Luna fights them with Man's stats)",
                "  1 attackable kinds with a placeholder combat definition",
                "  1 attackable kinds without a drop table",
                "  1 npc options and 2 object options that reach no handler",
            ),
            files.getValue(ContentAuditReport.OVERVIEW).lines().subList(5, 10),
        )
    }

    @Test
    fun `the overview opens with its date and a column header`() {
        val lines = files(lumbridge).getValue(ContentAuditReport.OVERVIEW).lines()

        assertEquals(
            listOf(
                "Content audit, 2026-10-02",
                "area|npcs|no combat|placeholder|no drops|npc opts|objects|object opts",
            ),
            listOf(lines.first(), lines[13].split(COLUMN_GAP).joinToString("|")),
        )
    }

    @Test
    fun `an overview row counts a zone's findings per kind`() {
        assertEquals(listOf("lumbridge", "4", "1", "1", "1", "1", "6", "2"), overviewRows(lumbridge).first())
    }

    @Test
    fun `the unzoned row adds up every region outside the zones`() {
        val rows = overviewRows(
            unzoned(UNZONED_SOUTH, listOf(Shortfall(goblin, 1))),
            unzoned(UNZONED_NORTH, listOf(Shortfall(goblin, 2))),
        )

        assertEquals(listOf("unzoned (2 regions)", "2", "2", "0", "0", "0", "0", "0"), rows.single())
    }

    private companion object {
        /** Columns of the overview are at least two spaces apart; names inside a column have single spaces. */
        val COLUMN_GAP = Regex(" {2,}")
    }
}
