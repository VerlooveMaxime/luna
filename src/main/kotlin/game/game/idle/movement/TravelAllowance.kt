package game.idle.movement

import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend

/**
 * What the autopilot's trips may spend besides walking and free passages, which is nothing unless the flow allows it
 * (Q1, Maxime 2026-10-17): [coins] for tolls and fares, [runes] for teleport spells, [charges] of teleport jewellery.
 */
data class TravelAllowance(val coins: Boolean = false, val runes: Boolean = false, val charges: Boolean = false) {

    fun allows(choice: TravelChoice): Boolean =
        when (choice) {
            TravelChoice.COINS -> coins
            TravelChoice.RUNES -> runes
            TravelChoice.CHARGES -> charges
        }

    /** This allowance with [choice] switched to [allowed], as the builder's Travel tab sets it (Part 3b). */
    fun with(choice: TravelChoice, allowed: Boolean): TravelAllowance =
        when (choice) {
            TravelChoice.COINS -> copy(coins = allowed)
            TravelChoice.RUNES -> copy(runes = allowed)
            TravelChoice.CHARGES -> copy(charges = allowed)
        }

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

    /**
     * [carried] as [ALL] would let a trip spend it, when that is more than this allows (less what [reserved] holds back
     * either way): what a quicker trip than the flow's rules allow is looked for on (the long way round, Q6). Null when
     * the rules keep nothing the player could pay with from the trip.
     */
    fun beyond(carried: CapabilitySnapshot, reserved: Spend, teleports: List<TeleportDefinition>): CapabilitySnapshot? {
        val allowed = restrict(carried, reserved, teleports)
        val everything = ALL.restrict(carried, reserved, teleports)
        val more = everything.items != allowed.items || everything.usableTeleports != allowed.usableTeleports || everything.charges != allowed.charges
        return everything.takeIf { more }
    }

    companion object {
        /** What every flow allows until its Travel setting says otherwise. */
        val NONE = TravelAllowance()

        /** Everything allowed: what a trip would do without the flow's rules, which the long-way line compares with. */
        val ALL = TravelAllowance(coins = true, runes = true, charges = true)
    }
}

/** One choice of a flow's Travel setting: [COINS] for tolls and fares, [RUNES] for teleport spells, [CHARGES] for teleport jewellery. */
enum class TravelChoice {
    COINS,
    RUNES,
    CHARGES,
    ;

    companion object {

        /** The choice a trip's [leg] spends by: coins for a toll or a fare, runes for a spell, charges for jewellery; null for a free one. */
        fun of(leg: PlanLeg): TravelChoice? =
            when {
                leg.requirements.coins > 0 -> COINS
                leg.teleport?.kind == TeleportKind.SPELL -> RUNES
                leg.teleport?.kind == TeleportKind.JEWELLERY -> CHARGES
                else -> null
            }
    }
}
