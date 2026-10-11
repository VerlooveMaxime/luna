package game.idle.flow

import game.idle.location.Tile
import kotlin.reflect.KClass

/** What a decision aims at: a target's [key] (its object id, npc index or item id) on its [tile]. */
data class Aim(val key: Int, val tile: Tile)

/** A decision aimed at one target, [distance] walking steps away (0 when it can be used from here). */
interface Aimed {

    val aim: Aim

    val distance: Int
}

/**
 * One retry rule for every kind (Maxime, 2026-10-11): a target that gets the same decision twice in a row with nothing
 * gained since (the kind's [progress] reading unchanged, the target no step closer) is set aside for the rest of the
 * turn, so an unreachable tree or a rock that refuses cannot trap the player. A kind decides through [decide], keeping
 * only its own filter of the aims set aside.
 */
class RetrySkip private constructor(private val progress: () -> Int) {

    private val skipped = mutableSetOf<Aim>()
    private var last: Attempt? = null

    /** [decide]s over the aims set aside; a retry sets its aim aside and decides again. */
    fun <D : Any> decide(decide: (Set<Aim>) -> D): D {
        val first = decide(skipped)
        val retried = retried(first)
        if (retried != null) skipped += retried
        val decision = if (retried == null) first else decide(skipped)
        last = (decision as? Aimed)?.let { Attempt(it::class, it.aim, it.distance, progress()) }
        return decision
    }

    private fun retried(decision: Any): Aim? {
        val attempt = decision as? Aimed ?: return null
        val before = last ?: return null
        val same = attempt::class == before.kind && attempt.aim == before.aim
        val gained = progress() != before.progress || attempt.distance < before.distance
        return attempt.aim.takeIf { same && !gained }
    }

    private data class Attempt(val kind: KClass<out Aimed>, val aim: Aim, val distance: Int, val progress: Int)

    companion object {

        /** The rule for objects and items, which stay where they are: an aim set aside stays so for the turn. */
        fun forTheTurn(progress: () -> Int): RetrySkip = RetrySkip(progress)
    }
}
