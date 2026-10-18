package game.idle.flow

import game.idle.location.Tile
import game.idle.movement.TravelChoice

/**
 * What carries out one step of a flow, decision by decision: it knows when the step is over, why it cannot go on, why
 * the flow must stop, and what it has to tell the player. Everything it reports is typed; the runner puts it into words
 * ([StepWords]), so no kind of step holds player text.
 */
interface StepActivity {

    fun isBusy(): Boolean

    fun act()

    fun isDone(): Boolean

    /** Why the step cannot go on until the player or the flow fixes it, or null while it can. */
    fun blocked(): Reason?

    /** What the step waits for the world to supply (a tree growing back), or null: shown in yellow, never told. */
    fun waiting(): Reason?

    /** What a trip of the step does on its way (opening a door), or null: shown in white while it lasts, never told. */
    fun underWay(): UnderWay?

    /** Why the whole flow must stop now, or null to carry on. */
    fun stopping(): StepStop?

    /** What the step has to tell the player since it was last asked, once; null for nothing. */
    fun said(): Said?

    /** How much of what its Amount counts the step did this turn, 0 for a step that counts nothing. */
    fun amountDone(): Int

    /** Whether the step's own work started this turn (a chop, a drop, a rest). */
    fun worked(): Boolean

    /** Ends the turn as reaching the Amount does: the unit in progress settles, the action is broken off, the step is over. */
    fun endTurn()
}

/**
 * Where a step's trips say what they do the moment they do it (a spend, a long way round), so a spend's line comes before
 * the game's own lines about it (Maxime, 2026-10-18).
 */
fun interface Speech {

    fun say(said: Said)
}

/**
 * What a step tells the player: it ended, or skipped its turn this lap, held by what the bag or the bank holds; or what
 * one of its trips did that the player should know of (a door it could not get through, a spend, a long way round).
 */
sealed interface Said {

    data class Ended(val reason: Reason.Stuck) : Said

    data class Skipped(val reason: Reason.Stuck) : Said

    /** A walk up to a target could not get through [passage] on [tile], so the target was set aside (Part 4b). */
    data class DoorShut(val passage: Passage, val tile: Tile) : Said

    /** A trip started a teleport (web-walker movement Part 3c). */
    data class Teleported(val started: TeleportStarted) : Said

    /** A trip agreed to pay [coins] at the Al Kharid toll gate. */
    data class PaidToll(val coins: Int) : Said

    /** A trip boards the ship to [place], for a fare of [coins] (none for Entrana's). */
    data class Fare(val coins: Int, val place: String) : Said

    /** A trip went the long way: what [choice] allows would have been quicker, and the flow does not allow it. */
    data class LongWay(val choice: TravelChoice) : Said
}

/** Why a step stops the flow: the step is [Stuck], or a [Reflex] of its ran away and stops the flow (S07c). */
sealed interface StepStop {

    data class Stuck(val reason: Reason.Stuck) : StepStop

    /** The reflex at [number] stopped the flow; [fired] is what it fired at. */
    data class Reflex(val number: Int, val fired: Fired) : StepStop
}
