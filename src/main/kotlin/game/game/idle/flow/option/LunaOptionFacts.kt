package game.idle.flow.option

import game.idle.idleState
import game.idle.location.Tile
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Reads a player's [OptionFacts]: unboosted levels unless the player counts boosted ones (the unboosted ones kept
 * beside), the bank's counts, what the bag holds and the player wears, where they stand, their combat level (Luna's,
 * over unboosted levels) and their spellbook.
 */
object LunaOptionFacts {

    fun of(player: Player): OptionFacts {
        val base = Skill.NAMES.indices.associateWith { player.skills.getSkill(it).staticLevel }
        val levels = if (player.idleState.countBoostedLevels) Skill.NAMES.indices.associateWith { player.skills.getSkill(it).level } else base
        val bank = player.bank.filterNotNull().groupingBy { it.id }.fold(0) { total, item -> total + item.amount }
        val bag = player.inventory.filterNotNull().map { it.id }.toSet()
        val worn = player.equipment.filterNotNull().map { it.id }.toSet()
        return OptionFacts(levels, bank, bag, worn, Tile.of(player.position), player.combatLevel, baseLevels = base, spellbook = player.spellbook)
    }
}

/** [GameNames] from the cache's definitions. */
object LunaGameNames : GameNames {

    override fun item(id: Int): String = ItemDefinition.ALL.retrieve(id).name

    override fun npc(id: Int): String = NpcDefinition.ALL.retrieve(id).name

    override fun obj(id: Int): String = GameObjectDefinition.ALL.retrieve(id).name
}
