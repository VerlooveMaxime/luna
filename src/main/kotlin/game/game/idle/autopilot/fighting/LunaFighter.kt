package game.idle.autopilot.fighting

import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.movement.Footprint
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * [Fighter] for a logged-in player fighting [target] within [area]. Attacks go through the same event as the client's
 * click, so Luna's combat and reach checks run unchanged. A kill is an npc of the target that died with the player's hit last on it, whether the step
 * attacked it or the player fought back.
 */
class LunaFighter(private val player: Player, private val target: FightTarget, private val area: Area) : Fighter, ReachTerrain<Npc> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)
    private var watched: Npc? = null
    private var lastKill: Npc? = null
    private var kills = 0

    override fun isBusy(): Boolean {
        watchKills()
        return !player.walking.isEmpty ||
            player.actions.first(InteractionAction::class.java) != null ||
            player.combat.target?.isAlive == true ||
            LunaClicks.hasBlockingWindow(player)
    }

    override fun look(): FightView =
        FightView(
            atLocation = scan.atLocation(player.position),
            targets = scan.reachable(player.position, candidates(), { Footprint(it.position, it.size(), it.size()) }, terrain = this)
                .map { (npc, reach) ->
                    TargetCandidate(npc.index, npc.position, reach.distance, reach.usableFromHere, reach.approach, attacking = attacksPlayer(npc))
                },
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

    override fun walkToLocation() {
        player.overlays.closeWindows(false)
        player.navigator.navigate(anchor, true)
    }

    override fun kills(): Int {
        watchKills()
        return kills
    }

    /** Luna's death drops the loot, then takes the npc out of the world (its respawn is a new npc), its death left queued. */
    override fun lastKillDying(): Boolean {
        watchKills()
        val npc = lastKill ?: return false
        return npc.state == EntityState.ACTIVE && npc.actions.first(MobDeathAction::class.java) != null
    }

    override fun stop() {
        player.actions.interruptWeak()
    }

    /** Counts the watched npc once it is dead, then watches whatever npc of the target the player fights now. */
    private fun watchKills() {
        val dead = watched?.takeIf { !it.isAlive }
        if (dead != null) {
            if (dead.combat.lastDamageReceived?.attacker == player) {
                kills++
                lastKill = dead
            }
            watched = null
        }
        val fighting = player.combat.target as? Npc
        if (fighting != null && fighting.isAlive && fighting.id in target.npcs) watched = fighting
    }

    /**
     * Npcs of the target nobody else is fighting, and whatever attacks the player, of any kind (Maxime, 2026-10-10: it is
     * fought back first; only the target's count as kills).
     */
    private fun candidates(): List<Npc> =
        world.locator.findNpcs(anchor, area.radius) { npc ->
            npc.isAlive && npc.combat.isAttackable && (attacksPlayer(npc) || npc.id in target.npcs && npc.combat.target == null)
        }.toList()

    private fun attacksPlayer(npc: Npc): Boolean = npc.combat.target == player

    /** An npc that wandered off since the look is still attacked; a dead one or another in its slot is not. */
    private fun find(candidate: TargetCandidate): Npc? =
        world.npcs.get(candidate.npcIndex)?.takeIf { it.isAlive && (it.id in target.npcs || attacksPlayer(it)) }

    /** The reach of the player's next attack: next to the npc for melee, in sight and range for a bow. */
    private fun attackPolicy(): InteractionPolicy {
        val weapon = player.combat.weapon
        val type = if (weapon.isRanged) InteractionType.LINE_OF_SIGHT else InteractionType.SIZE
        return InteractionPolicy(type, weapon.range)
    }

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    override fun reachedFrom(tile: Position, target: Npc): Boolean = world.collisionManager.reached(tile, target, attackPolicy())
}
