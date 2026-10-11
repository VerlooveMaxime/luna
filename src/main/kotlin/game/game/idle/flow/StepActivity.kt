package game.idle.flow

/**
 * What carries out one step of a flow, decision by decision: it knows when the step is over, why it cannot go on, why
 * the flow must stop, and what it has to tell the player. Everything it reports is typed; the runner puts it into words
 * ([StepWords]), so no kind of step holds player text.
 */
interface StepActivity {

    fun isBusy(): Boolean

    fun act()

    fun isDone(): Boolean

    /** Why the step cannot go on for now, or null while it can. */
    fun blocked(): Reason?

    /** Why the whole flow must stop now, or null to carry on. */
    fun stopping(): StepStop?

    /** What the step has to tell the player since it was last asked, once; null for nothing. */
    fun said(): Said?
}

/** What a step tells the player: it ended, or skipped its turn this lap, held by what the bag or the bank holds. */
sealed interface Said {

    data class Ended(val reason: Reason.Stuck) : Said

    data class Skipped(val reason: Reason.Stuck) : Said
}

/** Why a step stops the flow: the step is [Stuck], or a [Reflex] of its ran away and stops the flow (S07c). */
sealed interface StepStop {

    data class Stuck(val reason: Reason.Stuck) : StepStop

    /** The reflex at [number] fired at [health]. */
    data class Reflex(val number: Int, val health: Health) : StepStop
}
