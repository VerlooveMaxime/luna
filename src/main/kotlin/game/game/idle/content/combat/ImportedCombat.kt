package game.idle.content.combat

import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy

enum class CombatSource(val label: String) {
    LOSTCITY_377("LostCity 377"),
    LOSTCITY_289("LostCity 289"),
    OSRS("OSRS wiki, to review"),
}

/** An `aggression` object of `npc_combat.jsonc`. */
data class Aggression(val policy: NpcAggressionPolicy, val toleranceMinutes: Int) {

    companion object {
        /** Luna's rows pair each policy with one tolerance: 10 minutes for the combat level rule, none otherwise. */
        val BY_COMBAT_LEVEL = Aggression(NpcAggressionPolicy.COMBAT_LEVEL, toleranceMinutes = 10)
        val ALWAYS = Aggression(NpcAggressionPolicy.ALWAYS, toleranceMinutes = -1)
    }
}

/** What a source says about aggression: a value (null for none), or a hunt mode this importer cannot translate. */
sealed interface AggressionImport {

    data class Known(val aggression: Aggression?) : AggressionImport

    data class Unrecognised(val huntmode: String) : AggressionImport
}

/** A max hit and where it came from, or why there is none. */
data class MaximumHit(val value: Int?, val basis: String)

/** One npc's combat stats as a source gives them, in Luna's units: ticks, and Luna's skill and bonus order. */
data class ImportedCombat(
    val source: CombatSource,
    val hitpoints: Int,
    /** Attack, strength, defence, ranged, magic. */
    val skills: List<Int>,
    val attackSpeed: Int,
    val maximumHit: MaximumHit,
    val aggression: AggressionImport,
    val respawnTicks: Int?,
    val attackBonus: Int,
    val magicBonus: Int,
    val rangedBonus: Int,
    /** Stab, slash, crush, magic and ranged defence, then strength bonus. */
    val bonuses: List<Int>,
    val attackAnimation: Int?,
    val defenceAnimation: Int?,
    val deathAnimation: Int?,
)

/** How one cache npc is known to the sources: by id in ours, by name and combat level elsewhere. */
data class NpcIdentity(val id: Int, val name: String, val combatLevel: Int)
