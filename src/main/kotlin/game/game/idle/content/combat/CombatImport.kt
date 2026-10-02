package game.idle.content.combat

/** A field of a row that an import changed, both values as the report shows them. */
data class FieldChange(val field: String, val before: String, val after: String)

/** What happened to one npc. */
sealed interface ImportOutcome {
    val npc: NpcIdentity

    /** [changes] is empty when the row already held what the source says. */
    data class Updated(
        override val npc: NpcIdentity,
        val source: CombatSource,
        val row: NpcCombatRow,
        val changes: List<FieldChange>,
        val notes: List<String>,
    ) : ImportOutcome

    data class NoSource(override val npc: NpcIdentity) : ImportOutcome

    /** Luna falls back to Man's row for such an npc; adding a row is left to a person, who also names it. */
    data class NoRow(override val npc: NpcIdentity, val source: CombatSource) : ImportOutcome
}

/** Applies [sources] to the rows of `npc_combat.jsonc`, one npc at a time. */
class CombatImport(private val sources: CombatSources) {

    fun outcomes(file: NpcCombatFile, npcs: List<NpcIdentity>): List<ImportOutcome> = npcs.map { outcome(file, it) }

    private fun outcome(file: NpcCombatFile, npc: NpcIdentity): ImportOutcome {
        val imported = sources.find(npc) ?: return ImportOutcome.NoSource(npc)
        val row = file.row(npc.id) ?: return ImportOutcome.NoRow(npc, imported.source)
        val merged = merge(row, imported)
        return ImportOutcome.Updated(npc, imported.source, merged, changes(row, merged), notes(imported))
    }

    /** What a source does not give keeps the row's value: poison, and every field [imported] leaves null. */
    fun merge(row: NpcCombatRow, imported: ImportedCombat): NpcCombatRow = row.copy(
        respawnTicks = imported.respawnTicks ?: row.respawnTicks,
        aggression = aggression(row, imported.aggression),
        hitpoints = imported.hitpoints,
        maximumHit = imported.maximumHit.value ?: row.maximumHit,
        attackSpeed = imported.attackSpeed,
        attackAnimation = imported.attackAnimation ?: row.attackAnimation,
        defenceAnimation = imported.defenceAnimation ?: row.defenceAnimation,
        deathAnimation = imported.deathAnimation ?: row.deathAnimation,
        attackBonus = imported.attackBonus,
        magicBonus = imported.magicBonus,
        rangedBonus = imported.rangedBonus,
        skills = imported.skills,
        bonuses = imported.bonuses,
    )

    private fun aggression(row: NpcCombatRow, imported: AggressionImport): Aggression? =
        when (imported) {
            is AggressionImport.Known -> imported.aggression
            is AggressionImport.Unrecognised -> row.aggression
        }

    private fun notes(imported: ImportedCombat): List<String> {
        val aggression = imported.aggression
        val huntmode = if (aggression is AggressionImport.Unrecognised) {
            listOf("hunt mode ${aggression.huntmode} has no Luna policy, aggression kept")
        } else {
            emptyList()
        }
        return listOf("max hit: ${imported.maximumHit.basis}") + huntmode
    }

    private fun changes(before: NpcCombatRow, after: NpcCombatRow): List<FieldChange> =
        FIELDS.filter { (_, value) -> value(before) != value(after) }
            .map { (name, value) -> FieldChange(name, display(value(before)), display(value(after))) }

    private companion object {

        val FIELDS: List<Pair<String, (NpcCombatRow) -> Any?>> = listOf(
            "respawn_ticks" to { it.respawnTicks },
            "aggression" to { it.aggression },
            "hitpoints" to { it.hitpoints },
            "maximum_hit" to { it.maximumHit },
            "attack_speed" to { it.attackSpeed },
            "attack_animation" to { it.attackAnimation },
            "defence_animation" to { it.defenceAnimation },
            "death_animation" to { it.deathAnimation },
            "attack_bonus" to { it.attackBonus },
            "magic_bonus" to { it.magicBonus },
            "ranged_bonus" to { it.rangedBonus },
            "skills" to { it.skills },
            "bonuses" to { it.bonuses },
        )

        fun display(value: Any?): String =
            when (value) {
                null -> "none"
                is Aggression -> "${value.policy} (tolerance ${value.toleranceMinutes})"
                is List<*> -> value.joinToString("/")
                else -> value.toString()
            }
    }
}
