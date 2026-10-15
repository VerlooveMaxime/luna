package game.idle.autopilot.rest

import game.idle.flow.Health
import game.idle.flow.Meter
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork

/**
 * The `rest` step's work: resting is the work, so each decision below [percent] of full [health] is one, and the step
 * is over once hitpoints are back up to it, which Luna restores by itself (1 hitpoint a minute). Attacked, it keeps
 * resting: the game's auto-retaliate fights back and the step's reflexes eat, stop or jump (Maxime, 2026-10-11). The
 * hitpoints it gets back are its count.
 */
class RestWork(private val health: () -> Health, private val percent: Int) : StepWork {

    override val meter: Meter = Meter.Rises { health().hitpoints }

    override fun isBusy(): Boolean = false

    override fun finishing(): Boolean = false

    override fun interrupt() = Unit

    override fun act(left: Int?): StepOutcome = if (health().below(percent)) StepOutcome.Worked else StepOutcome.Finished
}
