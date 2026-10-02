package game.idle.autopilot

import game.idle.IdleState
import game.idle.autopilot.bank.BankActivity
import game.idle.autopilot.bank.LunaBanker
import game.idle.autopilot.drop.DropActivity
import game.idle.autopilot.drop.LunaItemDropper
import game.idle.autopilot.woodcutting.LunaWoodcutter
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import game.idle.flow.FlowPlayer
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivities
import game.idle.flow.StepActivity
import game.idle.idleState
import game.idle.ui.IdleUi
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

class LunaAutopilotPlayer(val player: Player) : AutopilotPlayer, FlowPlayer, StepActivities {

    override val username: String
        get() = player.username

    /** Every state change refreshes the overlay, so start, stop and step changes all show without extra wiring. */
    override var idleState: IdleState
        get() = player.idleState
        set(value) {
            player.idleState = value
            IdleUi.refresh(player, value)
        }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    override fun saveStep(index: Int) {
        idleState = idleState.atStep(index)
    }

    override fun chop(step: ResolvedStep.Chop): StepActivity =
        WoodcuttingActivity(LunaWoodcutter(player, step.spot), step.action)

    override fun drop(step: ResolvedStep.Drop): StepActivity = DropActivity(LunaItemDropper(player, step.itemIds))

    override fun bank(step: ResolvedStep.Bank): StepActivity =
        BankActivity(LunaBanker(player, step.booth.toPosition()))
}

class WorldTickScheduler(private val world: World) : TickScheduler {

    override fun everyTick(action: () -> Unit): ScheduledTick {
        val task = object : Task(1) {
            override fun execute() = action()
        }
        world.schedule(task)
        return ScheduledTick(task::cancel)
    }
}
