package game.skill.firemaking

import api.predef.ext.*
import game.player.Animations
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [Action] that allows a player to perform a generic firemaking based light action, where the end result
 * is determined by child classes. The tinderbox is struck every [Firemaking.ATTEMPT_TICKS] ticks, the first chance
 * of the fire catching [Firemaking.FIRST_ATTEMPT_TICKS] ticks after the first strike, until it catches.
 *
 * @author lare96
 */
abstract class LightAction(plr: Player) : Action<Player>(plr, ActionType.WEAK, false, 1) {

    // TODO@0.5.0 Implement correct sounds: FLINT1, FIRE_LIT, TINDERBOX_STRIKE(2017).

    /**
     * The ticks left until the next chance of the fire catching.
     */
    private var ticksUntilAttempt = Firemaking.FIRST_ATTEMPT_TICKS

    override fun onSubmit() {
        if (!mob.inventory.contains(Firemaking.TINDERBOX)) {
            mob.sendMessage("You need a tinderbox to light this.")
            complete()
        } else if (!canLight()) {
            complete()
        } else {
            mob.animation(Animations.FIREMAKING)
        }
    }

    override fun run(): Boolean {
        if (--ticksUntilAttempt > 0) {
            return false
        }
        if (catches()) {
            onLight()
            return true
        }
        mob.animation(Animations.FIREMAKING)
        ticksUntilAttempt = Firemaking.ATTEMPT_TICKS
        return false
    }

    override fun onFinished() {
        mob.animation(Animation.CANCEL)
    }

    /**
     * Determines what happens when the fire catches.
     */
    abstract fun onLight()

    /**
     * Further requirements for this action to proceed.
     */
    open fun canLight(): Boolean = true

    /**
     * Whether this strike of the tinderbox lights the fire.
     */
    protected open fun catches(): Boolean = Firemaking.catches(mob)
}
