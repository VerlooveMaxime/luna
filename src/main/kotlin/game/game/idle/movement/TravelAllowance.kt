package game.idle.movement

import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.Spend

/**
 * What the autopilot's trips may spend besides walking and free passages, which is nothing unless the flow allows it
 * (Q1, Maxime 2026-10-17): [coins] for tolls and fares, [runes] for teleport spells, [charges] of teleport jewellery.
 */
data class TravelAllowance(val coins: Boolean = false, val runes: Boolean = false, val charges: Boolean = false) {

    /**
     * [carried] (what the player carries and can do) as the planner may spend it on a trip: coins only when [coins] is
     * allowed, spells only with [runes], jewellery only with [charges], never the home teleport, and never what
     * [reserved] holds back for the flow's teleport reflexes (Q2). [teleports] tells each teleport's kind.
     */
    fun restrict(carried: CapabilitySnapshot, reserved: Spend, teleports: List<TeleportDefinition>): CapabilitySnapshot {
        val kinds = teleports.associate { it.id to it.kind }
        val usable = carried.usableTeleports.filter { id ->
            when (kinds[id]) {
                TeleportKind.SPELL -> runes
                TeleportKind.JEWELLERY -> charges
                TeleportKind.HOME, null -> false
            }
        }.toSet()
        val items = carried.items.mapValues { (id, amount) -> amount - (reserved.items[id] ?: 0) }
            .filter { (id, amount) -> amount > 0 && (coins || id != CapabilitySnapshot.COINS) }
        val charged = if (charges) {
            carried.charges.mapValues { (name, amount) -> amount - (reserved.charges[name] ?: 0) }.filterValues { it > 0 }
        } else {
            emptyMap()
        }
        return CapabilitySnapshot(
            skillLevels = carried.skillLevels,
            items = items,
            flags = carried.flags,
            wildernessLevel = carried.wildernessLevel,
            usableTeleports = usable,
            charges = charged,
            teleportSpends = carried.teleportSpends.filterKeys { it in usable },
        )
    }

    companion object {
        /** What every flow allows until its Travel setting says otherwise. */
        val NONE = TravelAllowance()
    }
}
