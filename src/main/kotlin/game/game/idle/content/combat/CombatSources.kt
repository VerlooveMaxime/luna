package game.idle.content.combat

/**
 * Finds one npc's combat stats: LostCity's 377 branch by id (its ids are ours), then the 289 branch through the 377
 * debugname when the display names agree (a debugname is sometimes reused for another npc), then OSRS by name and
 * combat level. Cross-checked on 2026-10-02: LostCity and OSRS agree on 95 to 98% of stats, 2009scape does not, so
 * it is not a source. Animations always map through the 377 branch, whose sequence ids are ours.
 */
class CombatSources(
    private val lostCity377: LostCityBranch,
    private val lostCity289: LostCityBranch,
    private val osrs: OsrsMonsters,
) {

    /** Null when no source has the npc. */
    fun find(npc: NpcIdentity): ImportedCombat? {
        val osrsMatch = osrs.matching(npc.name, npc.combatLevel)
        return fromLostCity(lostCity377.npc(npc.id), CombatSource.LOSTCITY_377, osrsMatch)
            ?: fromLostCity(olderBranchEntry(npc), CombatSource.LOSTCITY_289, osrsMatch)
            ?: osrsMatch?.let(::fromOsrs)
    }

    private fun olderBranchEntry(npc: NpcIdentity): ConfigBlock? =
        lostCity377.debugname(npc.id)
            ?.let(lostCity289::npcNamed)
            ?.takeIf { it.value("name").equals(npc.name, ignoreCase = true) }

    /** An entry without hitpoints has only the cache's fields: LostCity has not ported its combat yet. */
    private fun fromLostCity(entry: ConfigBlock?, source: CombatSource, osrsMatch: OsrsMonster?): ImportedCombat? {
        val hitpoints = entry?.value("hitpoints")?.toInt() ?: return null
        val params = entry.params
        val param = { name: String -> params[name]?.toInt() ?: 0 }
        val skills = SKILLS.map { entry.value(it)?.toInt() ?: DEFAULT_LEVEL }
        val sameMonster = osrsMatch?.takeIf {
            it.hitpoints == hitpoints && listOf(it.attack, it.strength, it.defence) == skills.take(3)
        }
        return ImportedCombat(
            source = source,
            hitpoints = hitpoints,
            skills = skills,
            attackSpeed = params["attackrate"]?.toInt() ?: DEFAULT_ATTACK_RATE,
            maximumHit = maximumHit(
                osrsHit = sameMonster?.maximumHit,
                damageType = params["damagetype"] ?: DEFAULT_DAMAGE_TYPE,
                skills = skills,
                param = param,
            ),
            aggression = aggression(entry.value("huntmode")),
            respawnTicks = entry.value("respawnrate")?.toInt() ?: DEFAULT_RESPAWN_TICKS,
            attackBonus = param("attackbonus"),
            magicBonus = param("magicattack"),
            rangedBonus = param("rangeattack"),
            bonuses = DEFENCES.map(param) + param("strengthbonus"),
            attackAnimation = params["attack_anim"]?.let { lostCity377.sequences[it] },
            defenceAnimation = params["defend_anim"]?.let { lostCity377.sequences[it] },
            deathAnimation = params["death_anim"]?.let { lostCity377.sequences[it] },
        )
    }

    /**
     * Luna stores one max hit while LostCity derives it per attack style, so OSRS's value wins when OSRS has the same
     * monster (same hitpoints, attack, strength and defence): it covers spellcasters with a melee damage type.
     */
    private fun maximumHit(osrsHit: Int?, damageType: String, skills: List<Int>, param: (String) -> Int): MaximumHit =
        when {
            osrsHit != null -> MaximumHit(osrsHit, "OSRS, same stats as LostCity")
            damageType == RANGED_STYLE ->
                MaximumHit(formula(skills[RANGED], param("rangebonus")), "LostCity ranged formula")
            damageType == MAGIC_STYLE -> MaximumHit(null, "magic: LostCity derives it from the spell, kept")
            else -> MaximumHit(formula(skills[STRENGTH], param("strengthbonus")), "LostCity melee formula")
        }

    /**
     * `cowardly` attacks unless the player's combat level is above twice the npc's outside the Wilderness, which is
     * Luna's COMBAT_LEVEL rule; the `aggressive_*` modes always attack.
     */
    private fun aggression(huntmode: String?): AggressionImport =
        when {
            huntmode == null -> AggressionImport.Known(null)
            huntmode == "cowardly" -> AggressionImport.Known(Aggression.BY_COMBAT_LEVEL)
            huntmode.startsWith("aggressive") -> AggressionImport.Known(Aggression.ALWAYS)
            else -> AggressionImport.Unrecognised(huntmode)
        }

    /** OSRS gives no respawn time or animations; an aggressive OSRS monster follows the combat level rule. */
    private fun fromOsrs(monster: OsrsMonster) = ImportedCombat(
        source = CombatSource.OSRS,
        hitpoints = monster.hitpoints,
        skills = listOf(monster.attack, monster.strength, monster.defence, monster.ranged, monster.magic),
        attackSpeed = monster.attackSpeed ?: DEFAULT_ATTACK_RATE,
        maximumHit = MaximumHit(monster.maximumHit, "OSRS"),
        aggression = AggressionImport.Known(Aggression.BY_COMBAT_LEVEL.takeIf { monster.aggressive }),
        respawnTicks = null,
        attackBonus = monster.attackBonus,
        magicBonus = monster.magicBonus,
        rangedBonus = monster.rangedBonus,
        bonuses = monster.defenceBonuses + monster.strengthBonus,
        attackAnimation = null,
        defenceAnimation = null,
        deathAnimation = null,
    )

    companion object {
        private val SKILLS = listOf("attack", "strength", "defence", "ranged", "magic")
        private val DEFENCES = listOf("stabdefence", "slashdefence", "crushdefence", "magicdefence", "rangedefence")
        private const val STRENGTH = 1
        private const val RANGED = 3

        // Defaults of LostCity's NpcType and its combat params (Engine-TS 377-wip, content 377-wip).
        private const val DEFAULT_LEVEL = 1
        private const val DEFAULT_ATTACK_RATE = 4
        private const val DEFAULT_RESPAWN_TICKS = 100
        private const val DEFAULT_DAMAGE_TYPE = "^crush_style"
        private const val RANGED_STYLE = "^ranged_style"
        private const val MAGIC_STYLE = "^magic_style"

        /** LostCity's `npc_melee_maxhit` and `npc_ranged_maxhit`: style bonus 1, no prayer, integer arithmetic. */
        fun formula(level: Int, bonus: Int): Int = ((level + 9) * (bonus + 64) + 320) / 640
    }
}
