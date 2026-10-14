package game.idle.danger

import engine.spawn.NpcSpawn
import engine.spawn.NpcSpawnFileParser
import game.idle.flow.option.GameNames
import game.idle.location.Tile
import io.luna.game.model.Position
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.NpcAggression
import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy
import io.luna.game.model.mob.combat.state.NpcCombatContext

/**
 * The [DangerCatalog] over Luna's data: the npc spawn file read through Luna's own parser, each spawn's aggression from
 * its combat definition and its level from the cache, named with [names]. Luna's rule ([NpcAggression]): an npc looks
 * for players within its aggression radius of where it stands, and it wanders up to its spawn's radius from its tile, so
 * the two add up to its reach; a boss attacks anyone in view.
 */
class LunaDangers(private val names: GameNames, private val boss: (Int) -> Boolean = NpcCombatContext::isBoss) {

    fun catalog(spawns: List<NpcSpawn> = NpcSpawnFileParser.readSpawns()): DangerCatalog = DangerCatalog(spawns.mapNotNull(::aggressive))

    private fun aggressive(spawn: NpcSpawn): AggressiveSpawn? {
        val profile = NpcCombatDefinition.ALL.get(spawn.id).orElse(null)?.aggression ?: return null
        val level = NpcDefinition.ALL.retrieve(spawn.id).combatLevel
        val reach = (spawn.wanderRadius ?: 0) + looks(profile.policy, boss(spawn.id))
        return AggressiveSpawn(names.npc(spawn.id), level, Tile.of(spawn.position), reach, aggression(profile.policy, level))
    }

    companion object {

        /** Luna's [policy] as the builder words it, for an npc at combat [level]. */
        fun aggression(policy: NpcAggressionPolicy, level: Int): Aggression =
            when (policy) {
                NpcAggressionPolicy.COMBAT_LEVEL -> Aggression.BelowCombat(level * 2 + 1)
                NpcAggressionPolicy.ALWAYS -> Aggression.Always
                NpcAggressionPolicy.WEARING_SARADOMIN -> Aggression.UnlessWorn("Saradomin")
                NpcAggressionPolicy.WEARING_GUTHIX -> Aggression.UnlessWorn("Guthix")
                NpcAggressionPolicy.WEARING_ZAMORAK -> Aggression.UnlessWorn("Zamorak")
            }

        /** How far an npc with [policy] looks for players; a [boss] attacks anyone in view. */
        fun looks(policy: NpcAggressionPolicy, boss: Boolean): Int =
            NpcAggression.aggressionRadius(policy, boss).takeIf { it > 0 } ?: Position.VIEWING_DISTANCE
    }
}
