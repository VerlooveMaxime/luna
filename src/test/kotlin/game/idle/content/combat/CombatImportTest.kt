package game.idle.content.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CombatImportTest {

    private val guardRow =
        row(id = 447, comment = "Jail guard", hitpoints = 32, attackSpeed = 7, aggression = COMBAT_LEVEL_TEN)

    private val imported = ImportedCombat(
        source = CombatSource.LOSTCITY_377,
        hitpoints = 32,
        skills = listOf(19, 23, 21, 1, 1),
        attackSpeed = 5,
        maximumHit = MaximumHit(3, "LostCity melee formula"),
        aggression = AggressionImport.Known(Aggression.ALWAYS),
        respawnTicks = 60,
        attackBonus = 9,
        magicBonus = 2,
        rangedBonus = 3,
        bonuses = listOf(8, 9, 10, 4, 9, 5),
        attackAnimation = 401,
        defenceAnimation = 404,
        deathAnimation = 2304,
    )

    private val import = CombatImport(
        CombatSources(
            branch(mapOf(447 to "jailguard"), "[jailguard]\nname=Jail guard\nhitpoints=32\n$GUARD_LEVELS"),
            branch(),
            OsrsMonsters(emptyList()),
        ),
    )

    private val file = NpcCombatFile.parse(COMBAT_FILE_TEXT)

    private val noSources = CombatImport(CombatSources(branch(), branch(), OsrsMonsters(emptyList())))

    @Test
    fun `merging takes every field the source gives`() {
        val merged = noSources.merge(guardRow, imported)

        assertEquals(
            guardRow.copy(
                respawnTicks = 60, aggression = Aggression.ALWAYS, maximumHit = 3, attackSpeed = 5,
                attackAnimation = 401, defenceAnimation = 404, deathAnimation = 2304, attackBonus = 9, magicBonus = 2,
                rangedBonus = 3, skills = listOf(19, 23, 21, 1, 1),
                bonuses = listOf(8, 9, 10, 4, 9, 5),
            ),
            merged,
        )
    }

    @Test
    fun `merging keeps what the source leaves unknown`() {
        val unknown = imported.copy(
            maximumHit = MaximumHit(null, "magic"),
            respawnTicks = null,
            attackAnimation = null,
            defenceAnimation = null,
        )
        val merged = import.merge(guardRow, unknown)

        assertEquals(
            listOf(1, 9, 422, 1834),
            listOf(merged.maximumHit, merged.respawnTicks, merged.attackAnimation, merged.defenceAnimation),
        )
    }

    @Test
    fun `merging keeps the row's aggression when the hunt mode has no policy`() {
        val unrecognised = imported.copy(aggression = AggressionImport.Unrecognised("gnomeball_tackler"))
        val merged = import.merge(guardRow, unrecognised)

        assertEquals(COMBAT_LEVEL_TEN, merged.aggression)
    }

    @Test
    fun `merging can take aggression away`() {
        assertEquals(null, import.merge(guardRow, imported.copy(aggression = AggressionImport.Known(null))).aggression)
    }

    @Test
    fun `merging never touches poison`() {
        val poisonous = guardRow.copy(poisonous = true, immunePoison = true)

        val merged = import.merge(poisonous, imported)

        assertEquals(listOf(true, true), listOf(merged.poisonous, merged.immunePoison))
    }

    @Test
    fun `an npc no source has is reported as such`() {
        assertEquals(listOf(ImportOutcome.NoSource(GIANT_RAT)), import.outcomes(file, listOf(GIANT_RAT)))
    }

    @Test
    fun `an npc without a row is reported with its source`() {
        val rowless = NpcCombatFile.parse(COMBAT_FILE_HEAD + MAN_BLOCK + "\n]\n")

        assertEquals(
            listOf(ImportOutcome.NoRow(JAIL_GUARD, CombatSource.LOSTCITY_377)),
            import.outcomes(rowless, listOf(JAIL_GUARD)),
        )
    }

    @Test
    fun `an updated npc lists the fields that changed, before and after`() {
        val outcome = import.outcomes(file, listOf(JAIL_GUARD)).single() as ImportOutcome.Updated

        assertEquals(
            listOf(
                FieldChange("respawn_ticks", "9", "100"),
                FieldChange("aggression", "COMBAT_LEVEL (tolerance 10)", "none"),
                FieldChange("maximum_hit", "1", "3"),
                FieldChange("attack_speed", "7", "4"),
                FieldChange("attack_bonus", "3", "0"),
                FieldChange("magic_bonus", "4", "0"),
                FieldChange("ranged_bonus", "5", "0"),
                FieldChange("bonuses", "8/9/10/4/9", "0/0/0/0/0/0"),
            ),
            outcome.changes,
        )
    }

    @Test
    fun `an updated npc carries the merged row`() {
        val outcome = import.outcomes(file, listOf(JAIL_GUARD)).single() as ImportOutcome.Updated

        assertEquals(listOf(447, 100, 3), listOf(outcome.row.id, outcome.row.respawnTicks, outcome.row.maximumHit))
    }

    @Test
    fun `an updated npc notes where its max hit came from`() {
        val outcome = import.outcomes(file, listOf(JAIL_GUARD)).single() as ImportOutcome.Updated

        assertEquals(listOf("max hit: LostCity melee formula"), outcome.notes)
    }

    @Test
    fun `a hunt mode without a policy is noted`() {
        val gnome = CombatImport(
            CombatSources(
                branch(mapOf(447 to "jailguard"), "[jailguard]\nhitpoints=32\nhuntmode=gnomeball_tackler\n"),
                branch(),
                OsrsMonsters(emptyList()),
            ),
        )
        val outcome = gnome.outcomes(file, listOf(JAIL_GUARD)).single() as ImportOutcome.Updated

        assertEquals("hunt mode gnomeball_tackler has no Luna policy, aggression kept", outcome.notes.last())
    }

    @Test
    fun `a row that already matches lists no change`() {
        val imported = NpcCombatFile.parse(file.withRows(listOf(firstImport())))
        val outcome = import.outcomes(imported, listOf(JAIL_GUARD)).single()

        assertEquals(emptyList<FieldChange>(), (outcome as ImportOutcome.Updated).changes)
    }

    private fun firstImport(): NpcCombatRow =
        (import.outcomes(file, listOf(JAIL_GUARD)).single() as ImportOutcome.Updated).row

    private companion object {
        const val GUARD_LEVELS = "attack=19\nstrength=23\ndefence=21\n"
    }
}
