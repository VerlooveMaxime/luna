package game.idle.autopilot

import api.bot.webwalk.plan.Spend
import api.predef.ext.scheduleOnce
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.flow.Teleports
import game.idle.idleState
import game.item.degradable.jewellery.JewelleryTeleports
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import game.skill.magic.teleportSpells.TeleportSpell
import game.skill.magic.teleportSpells.TeleportSpellAction
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player

/**
 * The autopilot's teleports, for the teleport reflex and a trip's teleport legs alike (web-walker movement Part 3c): a
 * spell starts as the spellbook's button would, jewellery as its menu would with the destination already picked, so
 * Luna's own checks, lines, runes, charges and experience apply. The 377 client offers no Operate on worn items, so a
 * worn piece is taken off into the bag, rubbed there and worn again once the player lands, as a 2006 player did
 * (Maxime, 2026-10-18); it counts only while the bag has room for it.
 */
object LunaTeleports {

    /** The equipment interface, whose first option on an item takes it off. */
    private const val EQUIPMENT = 1688

    /**
     * Luna's refusals, read before asking: not locked, not tele-blocked, not deeper in the wilderness than the teleport
     * leaves from; a spell on the player's spellbook, its level and runes as Luna counts them, the spellbook's click
     * throttle passed (read without restarting it, which `ready` does); a charged piece of the jewellery to rub.
     */
    fun canTeleport(player: Player, destination: TeleportDestination): Boolean {
        if (player.isLocked || player.status.isTeleBlocked() || player.wildernessLevel > Teleports.wildernessLimit(destination)) return false
        return when (destination) {
            is TeleportDestination.Spell -> {
                val spell = destination.spell
                player.spellbook == spell.style.spellbook && throttlePassed(player) && Magic.canCast(player, spell.level, spell.requirements)
            }
            is TeleportDestination.Jewellery -> piece(player, destination.jewellery) != null
        }
    }

    private fun throttlePassed(player: Player): Boolean {
        val delay = player.teleportDelay
        return !delay.isRunning || delay.durationTicks >= TeleportAction.CLICK_THROTTLE
    }

    /**
     * Starts the teleport to [destination], telling [starting] how it starts the moment before Luna's own lines; how it
     * started, null when Luna turned it down. Luna's action locks the player until they land.
     */
    fun teleport(player: Player, destination: TeleportDestination, starting: (TeleportStarted) -> Unit = {}): TeleportStarted? =
        when (destination) {
            is TeleportDestination.Spell -> cast(player, destination, starting)
            is TeleportDestination.Jewellery -> rub(player, destination, starting)
        }

    /** As the spellbook's button: a click within the throttle is dropped. What it uses up is read before the cast takes it. */
    private fun cast(player: Player, destination: TeleportDestination.Spell, starting: (TeleportStarted) -> Unit): TeleportStarted? {
        val spell = destination.spell
        val started = TeleportStarted.Cast(destination, Teleports.inSpellOrder(spell.requirements, cost(player, spell).orEmpty()))
        if (!player.teleportDelay.ready(TeleportAction.CLICK_THROTTLE)) return null
        starting(started)
        player.submitAction(TeleportSpellAction(player, spell))
        return started.takeIf { player.isLocked }
    }

    /** As the jewellery's menu, the destination already picked: Luna's rub line, then its teleport by the bag slot. */
    private fun rub(player: Player, destination: TeleportDestination.Jewellery, starting: (TeleportStarted) -> Unit): TeleportStarted? {
        val jewellery = destination.jewellery
        val piece = piece(player, jewellery) ?: return null
        val slot = when (piece) {
            is Piece.Carried -> piece.slot
            is Piece.Worn -> takeOff(player, piece) ?: return null
        }
        val started = TeleportStarted.Rubbed(destination, tookOff = piece is Piece.Worn)
        starting(started)
        player.sendMessage(jewellery.rub)
        JewelleryTeleports.teleport(player, destination.destination, slot, jewellery.items.indexOf(piece.id), jewellery)
        if (piece is Piece.Worn) wearAgain(player, slot, jewellery, piece.equipmentIndex)
        return started.takeIf { player.isLocked }
    }

    /** Takes [piece] off as the equipment tab's click does; the bag slot it went to, null when it stayed on. */
    private fun takeOff(player: Player, piece: Piece.Worn): Int? {
        LunaClicks.post(player, WidgetItemFirstClickEvent(player, piece.equipmentIndex, EQUIPMENT, piece.id))
        return bagSlot(player) { it == piece.id }
    }

    /**
     * Wears again what was taken off to rub, now with a charge less (or none left), once the player is free to click:
     * after the landing, or at once when Luna turned the teleport down. Only into the slot it came from while that is
     * still empty, and never for a player gone meanwhile.
     */
    private fun wearAgain(player: Player, slot: Int, jewellery: TeleportJewellery, equipmentIndex: Int) {
        player.world.scheduleOnce(1) {
            when {
                player.state != EntityState.ACTIVE -> Unit
                player.isLocked -> wearAgain(player, slot, jewellery, equipmentIndex)
                else -> wear(player, slot, jewellery, equipmentIndex)
            }
        }
    }

    private fun wear(player: Player, slot: Int, jewellery: TeleportJewellery, equipmentIndex: Int) {
        val item = player.inventory[slot] ?: return
        if (player.equipment[equipmentIndex] == null && (item.id in jewellery.items || item.id == jewellery.emptyId)) {
            LunaClicks.post(player, EquipItemEvent(player, slot, item.id, LunaClicks.INVENTORY))
        }
    }

    /** A piece of jewellery of [id] a rub can use: one in the bag, else a worn one while the bag has room to take it off into. */
    private sealed interface Piece {

        val id: Int

        data class Carried(val slot: Int, override val id: Int) : Piece

        data class Worn(val equipmentIndex: Int, override val id: Int) : Piece
    }

    /** The first charged piece of [jewellery] in the bag, else the worn one when the bag has a free slot. */
    private fun piece(player: Player, jewellery: TeleportJewellery): Piece? =
        carried(player, jewellery) ?: worn(player, jewellery)?.takeIf { player.inventory.computeRemainingSize() > 0 }

    private fun carried(player: Player, jewellery: TeleportJewellery): Piece.Carried? =
        (0 until player.inventory.capacity()).firstNotNullOfOrNull { slot ->
            player.inventory[slot]?.id?.takeIf { it in jewellery.items }?.let { Piece.Carried(slot, it) }
        }

    private fun worn(player: Player, jewellery: TeleportJewellery): Piece.Worn? =
        (0 until player.equipment.capacity()).firstNotNullOfOrNull { index ->
            player.equipment[index]?.id?.takeIf { it in jewellery.items }?.let { Piece.Worn(index, it) }
        }

    private fun bagSlot(player: Player, matches: (Int) -> Boolean): Int? =
        (0 until player.inventory.capacity()).firstOrNull { slot -> player.inventory[slot]?.id?.let(matches) == true }

    /**
     * The charges of teleport jewellery a rub can use now, by the jewellery's name, as the travel web's planner counts
     * them: every piece in the bag, and the worn one while the bag has room to take it off into.
     */
    fun charges(player: Player): Map<String, Int> {
        val room = player.inventory.computeRemainingSize() > 0
        return TeleportJewellery.entries.associate { jewellery ->
            val carried = (0 until player.inventory.capacity()).sumOf { slot -> chargesOf(jewellery, player.inventory[slot]?.id) }
            val worn = if (room) worn(player, jewellery)?.let { chargesOf(jewellery, it.id) } ?: 0 else 0
            jewellery.name to carried + worn
        }.filterValues { it > 0 }
    }

    private fun chargesOf(jewellery: TeleportJewellery, id: Int?): Int = if (id != null && id in jewellery.items) jewellery.charges(id) else 0

    /** What a trip keeps back for the flow's teleport reflexes (Q2): the next cast of each, summed. */
    fun reserve(player: Player): Spend {
        val state = player.idleState
        return Teleports.reserved(state.steps, state.reflexes).map { spend(player, it) }.fold(Spend.NONE) { total, next ->
            Spend(items = summed(total.items, next.items), charges = summed(total.charges, next.charges))
        }
    }

    private fun <K> summed(one: Map<K, Int>, other: Map<K, Int>): Map<K, Int> = (one.keys + other.keys).associateWith { (one[it] ?: 0) + (other[it] ?: 0) }

    /**
     * What the next teleport to [destination] takes, which a trip keeps back for the reflex that casts it (Q2): a spell
     * what Luna's rules take from the player now, or while they cannot cast it, what it lists that no worn staff gives;
     * jewellery one charge.
     */
    fun spend(player: Player, destination: TeleportDestination): Spend =
        when (destination) {
            is TeleportDestination.Spell -> {
                val spell = destination.spell
                val worn = (0 until player.equipment.capacity()).mapNotNull { player.equipment[it]?.id }.toSet()
                Spend(items = cost(player, spell) ?: Teleports.listed(spell.requirements, worn))
            }
            is TeleportDestination.Jewellery -> Spend(charges = mapOf(destination.jewellery.name to 1))
        }

    /** What casting [spell] would take from [player] by Luna's rules, beta mode's free casting aside; null while they cannot cast it. */
    private fun cost(player: Player, spell: TeleportSpell): Map<Int, Int>? =
        (Magic.spellCost(player, spell.level, spell.requirements) as? SpellCost.Castable)?.items
            ?.groupingBy { it.id }?.fold(0) { total, item -> total + item.amount }
}
