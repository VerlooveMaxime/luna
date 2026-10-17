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
 * gained since (the kind's [progress] reading unchanged, the target no step closer) is set aside, so an unreachable
 * tree or a rock that refuses cannot trap the player. How long it stays aside is the [targets]' way: objects and items
 * stay where they are, so for the rest of the turn ([forTheTurn]); npcs wander, so until something is gained (a kill)
 * or until every one around is set aside, when they all come back ([untilProgress]). A kind decides through [decide],
 * keeping only its own filter of the aims set aside.
 */
class RetrySkip private constructor(private val progress: () -> Int, private val targets: Targets, private val setAside: (Aim) -> Unit) {

    private val skipped = mutableSetOf<Aim>()
    private var last: Attempt? = null

    /** [decide]s over the aims set aside; a retry sets its aim aside and decides again. */
    fun <D : Any> decide(decide: (Set<Aim>) -> D): D {
        if (targets == Targets.WANDER && gained()) skipped.clear()
        val first = decide(skipped)
        val retried = retried(first)
        if (retried != null) {
            skipped += retried
            setAside(retried)
        }
        val decision = if (retried == null) first else decide(skipped)
        val taken = if (targets == Targets.WANDER && decision !is Aimed && skipped.isNotEmpty()) comeBack(decide) else decision
        last = (taken as? Aimed)?.let { Attempt(it::class, it.aim, it.distance, progress()) }
        return taken
    }

    private fun retried(decision: Any): Aim? {
        val attempt = decision as? Aimed ?: return null
        val before = last ?: return null
        val same = attempt::class == before.kind && attempt.aim == before.aim
        val gained = progress() != before.progress || attempt.distance < before.distance
        return attempt.aim.takeIf { same && !gained }
    }

    /** Whether the progress reading moved since the last aimed decision. */
    private fun gained(): Boolean {
        val before = last ?: return false
        return progress() != before.progress
    }

    /** Every aim set aside comes back, and the kind decides over none. */
    private fun <D : Any> comeBack(decide: (Set<Aim>) -> D): D {
        skipped.clear()
        return decide(skipped)
    }

    private data class Attempt(val kind: KClass<out Aimed>, val aim: Aim, val distance: Int, val progress: Int)

    private enum class Targets { STAY, WANDER }

    companion object {

        /**
         * The rule for objects and items, which stay where they are: an aim set aside stays so for the turn. [setAside]
         * hears of each aim set aside (a walk up stuck at a door tells it).
         */
        fun forTheTurn(progress: () -> Int, setAside: (Aim) -> Unit = {}): RetrySkip = RetrySkip(progress, Targets.STAY, setAside)

        /**
         * The rule for npcs, which wander (fight's rule, kept: one out of reach now may not be later): the aims set
         * aside come back once [progress] moves, a kill, or once every one around is set aside.
         */
        fun untilProgress(progress: () -> Int, setAside: (Aim) -> Unit = {}): RetrySkip = RetrySkip(progress, Targets.WANDER, setAside)
    }
}
