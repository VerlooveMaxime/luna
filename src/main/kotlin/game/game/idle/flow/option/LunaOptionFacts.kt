package game.idle.flow.option

import game.idle.idleState
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/** Reads a player's [OptionFacts]: unboosted levels unless the player counts boosted ones, and the bank's counts. */
object LunaOptionFacts {

    fun of(player: Player): OptionFacts {
        val boosted = player.idleState.countBoostedLevels
        val levels = Skill.NAMES.indices.associateWith { id ->
            player.skills.getSkill(id).let { if (boosted) it.level else it.staticLevel }
        }
        val bank = player.bank.filterNotNull().groupingBy { it.id }.fold(0) { total, item -> total + item.amount }
        return OptionFacts(levels, bank)
    }
}

/** [GameNames] from the cache's definitions. */
object LunaGameNames : GameNames {

    override fun item(id: Int): String = ItemDefinition.ALL.retrieve(id).name

    override fun npc(id: Int): String = NpcDefinition.ALL.retrieve(id).name

    override fun obj(id: Int): String = GameObjectDefinition.ALL.retrieve(id).name
}
