package game.idle.danger

import game.idle.location.Area
import game.idle.location.Tile

/** When an aggressive npc attacks a player, as Luna's aggression policies have it and the builder warns about it (S08). */
sealed interface Aggression {

    /** Attacks a player whose combat level is below [combat]: Luna attacks up to twice the npc's own level. */
    data class BelowCombat(val combat: Int) : Aggression

    /** Attacks every player. */
    data object Always : Aggression

    /** Attacks every player not wearing an item of [god] (Luna's WEARING_* policies). */
    data class UnlessWorn(val god: String) : Aggression

    /** Whether a player at combat level [combat] is attacked; what is worn is not judged (Maxime, 2026-10-14). */
    fun threatens(combat: Int): Boolean = this !is BelowCombat || combat < this.combat

    /** "attacks below combat 15", "attacks any level", "attacks unless a Zamorak item is worn"; [short] for the slot. */
    fun words(short: Boolean): String =
        when (this) {
            is BelowCombat -> if (short) "attacks below $combat" else "attacks below combat $combat"
            Always -> "attacks any level"
            is UnlessWorn -> if (short) "attacks unless $god is worn" else "attacks unless a $god item is worn"
        }
}

/**
 * Where an aggressive npc spawns: its [name] as the cache spells it, its combat [level], the [tile] it spawns on and how
 * far from it the npc attacks, its [reach] (as far as it wanders plus as far as it looks for players), and when.
 */
data class AggressiveSpawn(val name: String, val level: Int, val tile: Tile, val reach: Int, val aggression: Aggression) {

    val area: Area get() = Area(tile, reach)
}

/** One kind of aggressive npc within reach of a work area, as the builder names it: by name, level and aggression. */
data class Threat(val name: String, val level: Int, val aggression: Aggression) {

    /** The configure band's line: "Dark wizard wanders here, level 7, attacks below combat 15". */
    val line: String get() = "$name wanders here, level $level, ${aggression.words(short = false)}"

    /** The slot's line: "Dark wizard, level 7, attacks below 15". */
    val short: String get() = "$name, level $level, ${aggression.words(short = true)}"

    fun threatens(combat: Int): Boolean = aggression.threatens(combat)
}

/**
 * Every aggressive npc spawn of the world, read from the data once at boot (Luna's spawn file and npc combat
 * definitions, [LunaDangers]), so a warning does not change with where the npcs wander.
 */
class DangerCatalog(private val spawns: List<AggressiveSpawn>) {

    /** Every kind of aggressive npc that spawns, the lowest level first, once each. */
    val all: List<Threat> get() = threats(spawns)

    /** The kinds of aggressive npc within reach of any tile of [area], the lowest level first, once each. */
    fun threats(area: Area): List<Threat> = threats(spawns.filter { it.area.overlaps(area) })

    private fun threats(reaching: List<AggressiveSpawn>): List<Threat> =
        reaching.map { Threat(it.name, it.level, it.aggression) }.distinct().sortedWith(compareBy({ it.level }, { it.name }))

    companion object {
        val NONE = DangerCatalog(emptyList())
    }
}
