package game.idle.autopilot.fighting

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.NpcsAround
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * [Fighter] for a logged-in player fighting [target] within [area]. Attacks go through the same event as the client's
 * click, so Luna's combat and reach checks run unchanged. A kill is an npc of the target that died with the player's
 * hit last on it, whether the step attacked it or the player fought back.
 */
class LunaFighter(private val player: Player, private val target: FightTarget, private val area: Area, doorways: Doorways) : Fighter {

    private val around = NpcsAround(player, area, ::candidate, ::attackPolicy, doorways)
    private var watched: Npc? = null
    private var lastKill: Npc? = null
    private var kills = 0

    /**
     * Walking up to an npc or a click on its way, a live npc to fight, or a window open. Luna's combat action is WEAK and
     * lingers after the kill, so a fight is over once its target is dead, not when the action ends.
     */
    override fun isBusy(): Boolean {
        watchKills()
        return LunaClicks.interacting(player) || player.combat.target?.isAlive == true || LunaClicks.hasBlockingWindow(player)
    }

    override fun look(): FightView = FightView(around.atLocation(), around.candidates())

    /** An npc that wandered off since the look is still attacked; a dead one or another in its slot is not. */
    override fun attack(npc: Candidate) {
        val found = around.find(npc)?.takeIf { it.isAlive && (it.id in target.npcs || attacksPlayer(it)) } ?: return
        watched = found
        LunaClicks.interact(player, AttackNpcEvent(player, found), found, AttackNpcEvent::class.java)
    }

    /**
     * Walks to the tile the look found, inside the work area; the npc may wander off meanwhile, and the next look
     * finds where it went. Following it instead let Luna's pathfinder take long ways round fences.
     */
    override fun walkTo(npc: Candidate) = LunaClicks.approach(player, npc)

    override fun anchor(): Tile = area.anchor

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

    override fun interrupt() = LunaClicks.interrupt(player)

    /**
     * Counts the watched npc once it is dead, when it is one of the target's: an attacker of another kind fought back is
     * no kill. Then watches whatever npc of the target the player fights now.
     */
    private fun watchKills() {
        val dead = watched?.takeIf { !it.isAlive }
        if (dead != null) {
            if (dead.id in target.npcs && dead.combat.lastDamageReceived?.attacker == player) {
                kills++
                lastKill = dead
            }
            watched = null
        }
        val fighting = player.combat.target as? Npc
        if (fighting != null && fighting.isAlive && fighting.id in target.npcs) watched = fighting
    }

    /**
     * An npc of the target nobody else is fighting, or whatever attacks the player, of any kind (Maxime, 2026-10-10: it
     * is fought back first; only the target's count as kills).
     */
    private fun candidate(npc: Npc): Boolean =
        npc.isAlive && npc.combat.isAttackable && (attacksPlayer(npc) || npc.id in target.npcs && npc.combat.target == null)

    private fun attacksPlayer(npc: Npc): Boolean = npc.combat.target == player

    /** The reach of the player's next attack: next to the npc for melee, in sight and range for a bow. */
    private fun attackPolicy(): InteractionPolicy {
        val weapon = player.combat.weapon
        val type = if (weapon.isRanged) InteractionType.LINE_OF_SIGHT else InteractionType.SIZE
        return InteractionPolicy(type, weapon.range)
    }
}
