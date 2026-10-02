package game.idle.content.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CombatImportReportTest {

    private val report = CombatImportReport(listOf("12336", "12436"), LocalDate.of(2026, 10, 2))

    private val changed = ImportOutcome.Updated(
        npc = GIANT_RAT,
        source = CombatSource.LOSTCITY_377,
        row = row(id = 950),
        changes = listOf(FieldChange("hitpoints", "5", "3"), FieldChange("respawn_ticks", "9", "60")),
        notes = listOf("max hit: LostCity melee formula"),
    )

    private val unchanged = changed.copy(npc = JAIL_GUARD, source = CombatSource.OSRS, changes = emptyList())

    @Test
    fun `the report lists each change and note under its npc`() {
        val expected = """
            Npc combat import, 2026-10-02
            Areas: 12336, 12436
            Sources, in order: LostCity 377-wip by id; LostCity 289 by debugname when the name matches; OSRS wiki (osrsreboxed-db) by name and combat level, every value to review.

            Attackable npc kinds spawned there: 4. Changed: 1 (LostCity 377 1, LostCity 289 0, OSRS wiki, to review 0); already matching: 1; no source: 1; no row in npc_combat.jsonc: 1.

            Giant rat (950, level 3), from LostCity 377
              hitpoints: 5 -> 3
              respawn_ticks: 9 -> 60
              max hit: LostCity melee formula

            No source:
              Man (1, level 2)

            No row in npc_combat.jsonc (Luna fights them with Man's stats):
              Chicken (951, level 3), from LostCity 377

        """.trimIndent()
        val outcomes = listOf(
            changed,
            unchanged,
            ImportOutcome.NoSource(NpcIdentity(1, "Man", 2)),
            ImportOutcome.NoRow(NpcIdentity(951, "Chicken", 3), CombatSource.LOSTCITY_377),
        )

        assertEquals(expected, report.format(outcomes))
    }

    @Test
    fun `empty lists say none`() {
        assertEquals(
            listOf("No source:", NONE, "", "No row in npc_combat.jsonc (Luna fights them with Man's stats):", NONE),
            report.format(listOf(changed)).trimEnd().lines().takeLast(5),
        )
    }

    private companion object {
        const val NONE = "  none"
    }
}
