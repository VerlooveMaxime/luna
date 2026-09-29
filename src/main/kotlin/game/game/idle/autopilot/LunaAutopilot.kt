package game.idle.autopilot

import game.idle.IdleState
import game.idle.idleState
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

class LunaAutopilotPlayer(val player: Player) : AutopilotPlayer {

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
