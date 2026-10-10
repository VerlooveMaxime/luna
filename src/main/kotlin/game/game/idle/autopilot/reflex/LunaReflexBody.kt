package game.idle.autopilot.reflex

import game.idle.autopilot.LunaClicks
import game.idle.flow.Health
import game.idle.flow.ReflexBody
import game.idle.movement.FleePlanner
import game.player.item.consume.food.Food
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * [ReflexBody] for a logged-in player. A meal goes through the same event as the client's click, so Luna's eating runs
 * unchanged (its own delay between meals included) and the fight in progress goes on.
 */
class LunaReflexBody(private val player: Player) : ReflexBody {

    private val world get() = player.world

    override fun health(): Health = Health(player.health, player.skill(Skill.HITPOINTS).staticLevel)

    override fun foodSlot(foods: Set<Int>): Int? {
        val wanted = foods.ifEmpty { Food.ID_TO_FOOD.keys }
        return (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id in wanted }
    }

    override fun eat(slot: Int) {
        val food = player.inventory[slot] ?: return
        val event = ItemFirstClickEvent(player, food.id, slot, INVENTORY)
        if (LunaClicks.mayAct(player, event)) {
            player.overlays.closeWindows(false)
            player.plugins.post(event)
        }
    }

    override fun underAttack(): Boolean = threats().isNotEmpty()

    /**
     * The walk is found on the game thread, not another, so the player is on the move from the next tick and does not
     * turn to fight back; the reflex runs again at each decision until nothing attacks.
     */
    override fun flee(): Boolean {
        val tile = FleePlanner.pick(player.position, threats().map { it.position }, ::canStep) ?: return false
        player.actions.interruptWeak()
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, false)
        return true
    }

    override fun dead(): Boolean = player.health == 0

    /** Npcs fighting the player that can walk up to them. */
    private fun threats(): List<Npc> =
        world.locator.findNpcs(player.position, FleePlanner.THREAT_RANGE) { npc ->
            npc.isAlive && npc.combat.target == player && FleePlanner.canGetAt(npc.position, player.position, ::npcCanStep)
        }.toList()

    private fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    private fun npcCanStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.NPC, direction)

    private companion object {
        const val INVENTORY = 3214
    }
}
