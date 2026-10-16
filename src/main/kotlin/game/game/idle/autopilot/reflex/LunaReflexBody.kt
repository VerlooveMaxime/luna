package game.idle.autopilot.reflex

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.specialAttack.SpecialAttackHandler
import engine.combat.prayer.CombatPrayer
import engine.combat.status.StatusEffectType
import game.idle.autopilot.LunaClicks
import game.idle.flow.Health
import game.idle.flow.ReflexBody
import game.idle.flow.WornSpecial
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
 * [ReflexBody] for a logged-in player. Eating and drinking use the same event as the client's click, so Luna's
 * consumables run unchanged (their separate delays included) and the fight in progress goes on. Praying goes through
 * Luna's prayer set as the tab's buttons do (varps, sounds, head icon, drain), never to its state directly, and the
 * special attack through the bar as its button does (the varp, the drain, the special on the next hit).
 */
class LunaReflexBody(private val player: Player) : ReflexBody {

    private val world get() = player.world

    override fun health(): Health = healthOf(player)

    override fun level(skill: Int): Int = player.skill(skill).level

    override fun potionSlot(doses: List<Int>): Int? =
        doses.firstNotNullOfOrNull { id ->
            (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id == id }
        }

    override fun drink(slot: Int) = click(slot)

    override fun foodSlot(foods: Set<Int>): Int? {
        val wanted = foods.ifEmpty { Food.ID_TO_FOOD.keys }
        return (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id in wanted }
    }

    override fun eat(slot: Int) = click(slot)

    private fun click(slot: Int) {
        val item = player.inventory[slot] ?: return
        LunaClicks.postClosing(player, ItemFirstClickEvent(player, item.id, slot, LunaClicks.INVENTORY))
    }

    override fun praying(prayer: CombatPrayer): Boolean = player.combat.prayers.isActive(prayer)

    /**
     * Luna's own checks, read before asking: asking for a prayer that cannot be lit would still put out the one of its
     * group that is on. A locked player (a teleport, death) takes no clicks, the tab's included.
     */
    override fun canPray(prayer: CombatPrayer): Boolean {
        val skill = player.skill(Skill.PRAYER)
        return !player.isLocked && skill.staticLevel >= prayer.level && skill.level > 0 && StatusEffectType.DRAGON_SCIMITAR !in player.status
    }

    override fun pray(prayer: CombatPrayer): Boolean {
        player.combat.prayers.activateIfOff(prayer)
        return praying(prayer)
    }

    override fun stopPraying(prayer: CombatPrayer): Boolean = player.combat.prayers.deactivate(prayer, silent = false)

    override fun specialEnergy(): Int = player.combat.specialBar.energy

    /** Only a special Luna's scripts register counts: the bar of any other weapon lights nothing when clicked. */
    override fun special(): WornSpecial? {
        val type = player.combat.weapon.specialAttackType ?: return null
        return SpecialAttackHandler.of(type)?.let { WornSpecial(it.drain, attack = !it.activationOnly) }
    }

    /** Luna's own refusals, read before asking: `toggleOn` does nothing on a lit or locked bar. */
    override fun specialBarFree(): Boolean {
        val bar = player.combat.specialBar
        return !player.isLocked && !bar.isActivated && !bar.isLocked
    }

    override fun fighting(): Boolean = player.combat.target != null

    override fun useSpecial(): Boolean {
        player.combat.specialBar.toggleOn()
        return player.combat.specialBar.isActivated
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
