package game.idle.autopilot.reflex

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
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

/** The player's run toggle as it was before a flight turned running on, while the flight goes on; never saved. */
private var Player.runToggleBeforeFlight by Attr.nullableObj(Boolean::class)

/** [player]'s hitpoints out of their full hitpoints. */
fun healthOf(player: Player): Health = Health(player.health, player.skill(Skill.HITPOINTS).staticLevel)

/**
 * [ReflexBody] for a logged-in player. A meal goes through the same event as the client's click, so Luna's eating runs
 * unchanged (its own delay between meals included) and the fight in progress goes on.
 */
class LunaReflexBody(private val player: Player) : ReflexBody {

    private val world get() = player.world

    override fun health(): Health = healthOf(player)

    override fun foodSlot(foods: Set<Int>): Int? {
        val wanted = foods.ifEmpty { Food.ID_TO_FOOD.keys }
        return (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id in wanted }
    }

    override fun eat(slot: Int) {
        val food = player.inventory[slot] ?: return
        LunaClicks.postClosing(player, ItemFirstClickEvent(player, food.id, slot, LunaClicks.INVENTORY))
    }

    override fun underAttack(): Boolean = threats().isNotEmpty()

    /**
     * The walk is found on the game thread, not another, so the player is on the move from the next tick and does not
     * turn to fight back; the reflex runs again at each decision until nothing attacks. Running is turned on once, at
     * the flight's start and only with energy left (Maxime, 2026-10-10: no loop of turning it on); once Luna turns it
     * off, the energy gone, the flight walks.
     */
    override fun flee() {
        val tile = FleePlanner.pick(player.position, threats().map { it.position }, ::canStep) ?: return
        if (player.runToggleBeforeFlight == null) {
            player.runToggleBeforeFlight = player.isRunning
            if (player.runEnergy > 0.0) player.isRunning = true
        }
        player.actions.interruptWeak()
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, false)
    }

    override fun calm() {
        val before = player.runToggleBeforeFlight ?: return
        player.isRunning = before
        player.runToggleBeforeFlight = null
    }

    override fun dead(): Boolean = player.health == 0

    /** Npcs fighting the player that can walk up to them. */
    private fun threats(): List<Npc> =
        world.locator.findNpcs(player.position, FleePlanner.THREAT_RANGE) { npc ->
            npc.isAlive && npc.combat.target == player && FleePlanner.canGetAt(npc.position, player.position, ::npcCanStep)
        }.toList()

    private fun canStep(from: Position, direction: Direction): Boolean = LunaClicks.canStep(world, from, direction)

    private fun npcCanStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.NPC, direction)
}
