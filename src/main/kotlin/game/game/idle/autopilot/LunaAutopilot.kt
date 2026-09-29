package game.idle.autopilot

import api.predef.woodcutting
import game.idle.IdleState
import game.idle.autopilot.bank.BankActivity
import game.idle.autopilot.bank.LunaBanker
import game.idle.autopilot.woodcutting.LunaWoodcutter
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import game.idle.flow.FlowPlayer
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivities
import game.idle.flow.StepActivity
import game.idle.idleState
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

class LunaAutopilotPlayer(val player: Player) : AutopilotPlayer, FlowPlayer, StepActivities {

    override val username: String
        get() = player.username

    override var idleState: IdleState
        get() = player.idleState
        set(value) {
            player.idleState = value
        }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    override fun woodcuttingLevel(): Int = player.woodcutting.level

    override fun inventoryFull(): Boolean = player.inventory.isFull

    override fun countOwned(itemId: Int): Int =
        player.inventory.computeAmountForId(itemId) + player.bank.computeAmountForId(itemId)

    override fun saveStep(index: Int) {
        player.idleState = player.idleState.atStep(index)
    }

    override fun chop(step: ResolvedStep.Chop): StepActivity =
        WoodcuttingActivity(LunaWoodcutter(player, step.spot), step.action)

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
