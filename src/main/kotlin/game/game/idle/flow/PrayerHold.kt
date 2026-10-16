package game.idle.flow

import engine.combat.prayer.CombatPrayer

/**
 * The prayers the flow lit and keeps on, each with the reflex that lit it (S13 part 3, Maxime 2026-10-16). Held for the
 * whole run, not a step: a prayer stays on across steps while a reflex of the running step whose When holds still wants
 * it, and is put out once none does or the flow ends. A prayer that was on when the flow came to want it is the
 * player's and is never put out here; one the flow lit that went out elsewhere (zero points, the player's own click)
 * is let go without a word, since the flow did not put it out.
 */
class PrayerHold {

    private val held = linkedMapOf<CombatPrayer, Int>()

    fun lit(prayer: CombatPrayer, number: Int) {
        held[prayer] = number
    }

    /**
     * Whether a held prayer is on that none of the prayers [wanted] wants, so the next decision puts it out. What is
     * wanted is only worked out while something is held and on: the triggers behind it cost a search each.
     */
    fun pending(body: ReflexBody, wanted: () -> Set<CombatPrayer>): Boolean {
        val on = held.keys.filter(body::praying)
        if (on.isEmpty()) return false
        val wants = wanted()
        return on.any { it !in wants }
    }

    /** Lets go of every held prayer none of the prayers [wanted] wants, putting out those still on: a change for each put out. */
    fun release(body: ReflexBody, wanted: () -> Set<CombatPrayer>): List<PrayerChange> {
        if (held.isEmpty()) return emptyList()
        val wants = wanted()
        val gone = held.filterKeys { it !in wants }
        held.keys.removeAll(gone.keys)
        return gone.mapNotNull { (prayer, number) -> PrayerChange(number, prayer, on = false).takeIf { body.stopPraying(prayer) } }
    }

    /** The flow ends: every held prayer is put out. */
    fun releaseAll(body: ReflexBody): List<PrayerChange> = release(body) { emptySet() }
}
