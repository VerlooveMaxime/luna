package game.idle.autopilot

import game.idle.IdleState
import game.idle.flow.FlowPlayer
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.idleState
import game.idle.location.Tile
import game.idle.ui.IdleUi
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

class LunaAutopilotPlayer(val player: Player) : AutopilotPlayer, FlowPlayer {

    override val username: String
        get() = player.username

    /** Every state change refreshes the overlay, so start, stop and step changes all show without extra wiring. */
    override var idleState: IdleState
        get() = player.idleState
        set(value) {
            player.idleState = value
            IdleUi.refresh(player, value)
        }

    override val tile: Tile
        get() = Tile.of(player.position)

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    override fun saveStep(index: Int) {
        idleState = idleState.atStep(index)
    }

    override fun lapCompleted() {
        idleState = idleState.lapped()
    }

    /** A flow is started before its first activity, so the run tile is set; the player's tile only guards a gap. */
    override fun activity(step: ResolvedStep): StepActivity = step.activity(player, idleState.runTile ?: tile)
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
