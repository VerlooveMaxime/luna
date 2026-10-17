package game.idle.autopilot.cooking

import game.idle.autopilot.Candidate
import game.idle.autopilot.cooking.CookingDecision.CookAll
import game.idle.autopilot.cooking.CookingDecision.Settled
import game.idle.autopilot.cooking.CookingDecision.UseOn
import game.idle.autopilot.cooking.CookingDecision.WalkTo
import game.idle.autopilot.cooking.CookingDecision.WalkToLocation
import game.idle.autopilot.travel.WalkUps
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.RetrySkip
import game.idle.flow.Said
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.Target
import game.idle.location.Tile

/** What the cook step can see and do for one player. [LunaCooker] is the in-game one. */
interface Cooker {

    fun isBusy(): Boolean

    fun look(): CookingView

    /** Uses the raw food in [slot] on [place], which opens the cooking window. */
    fun useOn(place: Candidate, slot: Int)

    /** Picks "cook all" in the open cooking window. */
    fun cookAll()

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How much of the step's raw food the player carries. */
    fun raw(): Int

    /** Breaks off the cooking in progress. */
    fun interrupt()
}

/** What the cook step knows when it decides: [rawSlot] holds some of the step's raw food the player can cook, if any. */
data class CookingView(
    val rawSlot: Int?,
    val windowOpen: Boolean,
    val atLocation: Boolean,
    val places: List<Candidate>,
)

sealed interface CookingDecision {

    /** A decision aimed at one fire or range. */
    sealed interface OnPlace : CookingDecision, Aimed {
        val place: Candidate

        override val aim: Aim get() = place.aim

        override val distance: Int get() = place.distance
    }

    data class UseOn(override val place: Candidate, val slot: Int) : OnPlace

    data class WalkTo(override val place: Candidate) : OnPlace

    data object CookAll : CookingDecision

    data object WalkToLocation : CookingDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : CookingDecision
}

/**
 * Cook on the nearest fire or range, one in reach first, leaving out the places [RetrySkip] set aside, and walk back to
 * the work spot when none is in reach from outside it; with no raw food left the step has nothing to work with. The
 * level is the shared turn's to check ([game.idle.flow.StepTurn]). No fire or range in reach is a block, not a wait: the
 * world lights none, a light step or the player must (Maxime, 2026-10-11).
 */
object CookingPlanner {

    fun decide(view: CookingView, skipped: Set<Aim>): CookingDecision {
        val best = view.places.filterNot { it.aim in skipped }.minWithOrNull(Candidate.NEAREST_FIRST)
        return when {
            view.rawSlot == null -> Settled(StepOutcome.Empty)
            view.windowOpen -> CookAll
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.FIRE_OR_RANGE)))
            best.usableFromHere -> UseOn(best, view.rawSlot)
            else -> WalkTo(best)
        }
    }
}

/** The cook step's work: carries out [CookingPlanner] decisions, the raw food used (cooked or burnt) the count. */
class CookingWork(private val cooker: Cooker, private val walkUps: WalkUps) : StepWork {

    private val retry = RetrySkip.forTheTurn(cooker::raw, walkUps::setAside)

    override val meter: Meter = Meter.Falls(cooker::raw)

    override fun isBusy(): Boolean = walkUps.isBusy(cooker.isBusy())

    override fun finishing(): Boolean = false

    override fun interrupt() {
        walkUps.stop()
        cooker.interrupt()
    }

    override fun said(): Said? = walkUps.said()

    override fun act(left: Int?): StepOutcome {
        val view = cooker.look()
        val decision = retry.decide { skipped -> CookingPlanner.decide(view, skipped) }
        if (decision !is WalkTo) walkUps.stop()
        return carryOut(decision)
    }

    private fun carryOut(decision: CookingDecision): StepOutcome =
        when (decision) {
            is UseOn -> useOn(decision.place, decision.slot)
            is WalkTo -> walkUps.walkTo(decision.place)
            CookAll -> cookAll()
            WalkToLocation -> StepOutcome.Away(cooker.anchor())
            is Settled -> decision.outcome
        }

    private fun useOn(place: Candidate, slot: Int): StepOutcome {
        cooker.useOn(place, slot)
        return StepOutcome.Worked
    }

    private fun cookAll(): StepOutcome {
        cooker.cookAll()
        return StepOutcome.Worked
    }
}
