package game.idle.autopilot.fighting

import game.idle.content.audit.LunaKinds
import game.idle.content.audit.NpcKind

/**
 * Something the fight step can fight: every npc of [npcs] goes by [name] in a flow; [label] is how the cache spells it
 * and [levels] spans their combat levels.
 */
data class FightTarget(val name: String, val npcs: Set<Int>, val label: String, val levels: IntRange)

/**
 * Every npc a fight step can fight: the cache's attackable npcs, one target per name (Maxime, 2026-10-07), so the data
 * lives in one place. Npcs whose combat stats are still placeholders fight oddly until the combat import covers them.
 */
class FightTargetCatalog(val targets: List<FightTarget>) {

    private val byName: Map<String, FightTarget> = targets.associateBy { it.name }

    fun find(name: String): FightTarget? = byName[name]

    companion object {

        /** One target per lower-case name over the attackable [npcs]. */
        fun of(npcs: List<NpcKind>): FightTargetCatalog =
            FightTargetCatalog(
                npcs.filter { it.attackable }.groupBy { it.name.lowercase() }.map { (name, kinds) ->
                    val levels = kinds.map { it.combatLevel }
                    FightTarget(name, kinds.map { it.id }.toSet(), kinds.first().name, levels.min()..levels.max())
                },
            )

        /** Over the cache's npc definitions, which a booted server has loaded before its plugins. */
        fun fromCache(): FightTargetCatalog = of(LunaKinds.npcs())
    }
}
