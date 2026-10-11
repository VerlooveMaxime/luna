package game.idle.flow

import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * Transitional, until every kind is a [StepWork] (step behaviour refactor Parts 2-4): the shape a step activity had
 * before [StepTurn], which the kinds not yet converted still build.
 */
interface LegacyActivity {

    fun isBusy(): Boolean

    fun act()

    fun isDone(): Boolean

    fun blocked(): Reason?

    fun stopping(): StepStop?

    fun said(): Said?
}

/** Transitional: a legacy activity whose Amount counts something, as the reflexes' progress rule reads it. */
interface CountsAmount {

    fun amountDone(): Int
}

/** Transitional: a kind of step not yet converted, which builds its own [LegacyActivity]. */
interface LegacyKind : ResolvedStep {

    /** The activity that carries this step out for [player], whose flow was started on [runTile]. */
    fun activity(player: Player, runTile: Tile): LegacyActivity
}

/**
 * Transitional: a [LegacyActivity] run as the runner expects a [StepActivity]. It cannot wait, be ended early or say
 * whether it worked, so it answers as a kind that always works and never waits; its count is the legacy one's.
 */
class LegacyStep(val activity: LegacyActivity) : StepActivity {

    override fun isBusy(): Boolean = activity.isBusy()

    override fun act() = activity.act()

    override fun isDone(): Boolean = activity.isDone()

    override fun blocked(): Reason? = activity.blocked()

    override fun waiting(): Reason? = null

    override fun stopping(): StepStop? = activity.stopping()

    override fun said(): Said? = activity.said()

    override fun amountDone(): Int = if (activity is CountsAmount) activity.amountDone() else 0

    override fun worked(): Boolean = true

    override fun endTurn() = Unit
}
