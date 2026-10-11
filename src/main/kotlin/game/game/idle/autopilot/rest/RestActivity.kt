package game.idle.autopilot.rest

import game.idle.flow.CountsAmount
import game.idle.flow.Health
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepActivity
import game.idle.flow.StepStop

/**
 * The `rest` step: does nothing until [health] is back up to [percent] of full, which Luna restores by itself (1
 * hitpoint a minute). Attacked, it keeps resting: the game's auto-retaliate fights back and the step's reflexes eat or
 * run (Maxime, 2026-10-11). The hitpoints it gets back count as its progress for the reflexes' "cut short again" rule.
 */
class RestActivity(private val health: () -> Health, private val percent: Int) : StepActivity, CountsAmount {

    private val started = health().hitpoints

    override fun isBusy(): Boolean = false

    override fun isDone(): Boolean = !health().below(percent)

    override fun blocked(): Reason? = null

    override fun stopping(): StepStop? = null

    override fun said(): Said? = null

    override fun act() = Unit

    override fun amountDone(): Int = (health().hitpoints - started).coerceAtLeast(0)
}
