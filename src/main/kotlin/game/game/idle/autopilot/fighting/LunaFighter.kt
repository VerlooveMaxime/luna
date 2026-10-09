package game.idle.autopilot.fighting

import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.movement.FleePlanner
import game.idle.movement.Footprint
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.player.item.consume.food.Food
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * [Fighter] for a logged-in player fighting [target] within [area]. Attacks and meals go through the same events as
 * the client's clicks, so Luna's combat, reach checks and eating run unchanged; a meal leaves the fight going, as a
 * 2006 player's did. A kill is an npc of the target that died with the player's hit last on it, whether the step
 * attacked it or the player fought back.
 */
class LunaFighter(private val player: Player, private val target: FightTarget, private val area: Area) : Fighter, ReachTerrain<Npc> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)
    private var watched: Npc? = null
    private var kills = 0

    override fun isBusy(): Boolean {
        watchKills()
        return !player.walking.isEmpty ||
            player.actions.first(InteractionAction::class.java) != null ||
            player.combat.target?.isAlive == true ||
            LunaClicks.hasBlockingWindow(player)
    }

    override fun health(): Health = Health(player.health, player.skill(Skill.HITPOINTS).staticLevel)

    override fun look(): FightView =
        FightView(
            health = health(),
            foodSlot = (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id in Food.ID_TO_FOOD },
            threats = threats().size,
            atLocation = scan.atLocation(player.position),
            targets = scan.reachable(player.position, candidates(), { Footprint(it.position, it.size(), it.size()) }, terrain = this)
                .map { (npc, reach) -> TargetCandidate(npc.index, npc.position, reach.distance, reach.usableFromHere, reach.approach) },
        )

    override fun attack(target: TargetCandidate) {
        val npc = find(target) ?: return
        watched = npc
        LunaClicks.interact(player, AttackNpcEvent(player, npc), npc, AttackNpcEvent::class.java)
    }

    /**
     * Walks to the tile the look found, inside the work area; the npc may wander off meanwhile, and the next look
     * finds where it went. Following it instead let Luna's pathfinder take long ways round fences.
     */
    override fun walkTo(target: TargetCandidate) {
        player.overlays.closeWindows(false)
        player.navigator.navigate(target.approach, true)
    }

    override fun eat(slot: Int) {
        val food = player.inventory[slot] ?: return
        val event = ItemFirstClickEvent(player, food.id, slot, INVENTORY)
        if (LunaClicks.mayAct(player, event)) {
            player.overlays.closeWindows(false)
            player.plugins.post(event)
        }
    }

    /**
     * The walk is found on the game thread, not another, so the player is on the move from the next tick and does not
     * turn to fight back; while hitpoints stay low the step runs again at each decision.
     */
    override fun flee(): Boolean {
        val tile = FleePlanner.pick(player.position, threats().map { it.position }, ::canStep) ?: return false
        player.actions.interruptWeak()
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, false)
        return true
    }

    override fun walkToLocation() {
        player.overlays.closeWindows(false)
        player.navigator.navigate(anchor, true)
    }

    override fun kills(): Int {
        watchKills()
        return kills
    }

    override fun stop() {
        player.actions.interruptWeak()
    }

    /** Counts the watched npc once it is dead, then watches whatever npc of the target the player fights now. */
    private fun watchKills() {
        val dead = watched?.takeIf { !it.isAlive }
        if (dead != null) {
            if (dead.combat.lastDamageReceived?.attacker == player) kills++
            watched = null
        }
        val fighting = player.combat.target as? Npc
        if (fighting != null && fighting.isAlive && fighting.id in target.npcs) watched = fighting
    }

    /** Npcs of the target nobody else is fighting. */
    private fun candidates(): List<Npc> =
        world.locator.findNpcs(anchor, area.radius) { npc ->
            npc.id in target.npcs && npc.isAlive && npc.combat.isAttackable && npc.combat.target.let { it == null || it == player }
        }.toList()

    /** Npcs fighting the player that can walk up to them. */
    private fun threats(): List<Npc> =
        world.locator.findNpcs(player.position, FleePlanner.THREAT_RANGE) { npc ->
            npc.isAlive && npc.combat.target == player && FleePlanner.canGetAt(npc.position, player.position, ::npcCanStep)
        }.toList()

    /** An npc that wandered off since the look is still attacked; a dead one or another in its slot is not. */
    private fun find(candidate: TargetCandidate): Npc? =
        world.npcs.get(candidate.npcIndex)?.takeIf { it.id in target.npcs && it.isAlive }

    /** The reach of the player's next attack: next to the npc for melee, in sight and range for a bow. */
    private fun attackPolicy(): InteractionPolicy {
        val weapon = player.combat.weapon
        val type = if (weapon.isRanged) InteractionType.LINE_OF_SIGHT else InteractionType.SIZE
        return InteractionPolicy(type, weapon.range)
    }

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    private fun npcCanStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.NPC, direction)

    override fun reachedFrom(tile: Position, target: Npc): Boolean = world.collisionManager.reached(tile, target, attackPolicy())

    private companion object {
        const val INVENTORY = 3214
    }
}
