package game.idle.autopilot

import game.idle.IdleState
import game.idle.autopilot.reflex.LunaReflexBody
import game.idle.autopilot.travel.stepSpeech
import game.idle.autopilot.travel.tripTo
import game.idle.flow.Counts
import game.idle.flow.Gathers
import game.idle.flow.FlowPlayer
import game.idle.flow.OwnSpot
import game.idle.flow.Place
import game.idle.flow.ReflexBody
import game.idle.flow.ResolvedStep
import game.idle.flow.Speech
import game.idle.flow.RunProgress
import game.idle.flow.SpotWalk
import game.idle.flow.StepActivity
import game.idle.flow.StepTurn
import game.idle.flow.StepWork
import game.idle.idleState
import game.idle.location.Tile
import game.idle.ui.IdleUi
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

/** A Luna player as the autopilot sees it; [ui] shows every change of their idle state. */
class LunaAutopilotPlayer(val player: Player, private val ui: IdleUi) : AutopilotPlayer, FlowPlayer {

    override val username: String
        get() = player.username

    /** Every state change refreshes the overlay, so start, stop and step changes all show without extra wiring. */
    override var idleState: IdleState
        get() = player.idleState
        set(value) {
            player.idleState = value
            ui.refresh(player, value)
        }

    override val tile: Tile
        get() = Tile.of(player.position)

    override val body: ReflexBody = LunaReflexBody(player)

    private val carried = LunaCarried(player)

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    /**
     * Every walk of the autopilot goes through Luna's navigator, and a walk the player clicks never does; a flight's
     * running ends with it.
     */
    override fun endWalk() {
        body.calm()
        LunaClicks.endWalk(player)
    }

    override fun saveStep(index: Int) {
        idleState = idleState.atStep(index)
    }

    override fun saveProgress(progress: RunProgress) {
        idleState = idleState.withProgress(progress)
    }

    override fun lapCompleted() {
        idleState = idleState.lapped()
    }

    /** The base level: a boost never meets a Level goal (Maxime, 2026-10-15). */
    override fun level(skill: Int): Int = player.skill(skill).staticLevel

    override fun banked(items: Set<Int>): Int = items.sumOf { player.bank.computeAmountForId(it) }

    /**
     * A flow is started before its first activity, so the run tile is set; the player's tile only guards a gap. A step
     * with its own work spot starting outside its area walks there first (S07e). The one place a step's turn is put
     * together: the kind's work inside [StepTurn]. Its trips, made now or later in the turn, speak through [speech].
     */
    override fun activity(step: ResolvedStep, speech: Speech): StepActivity {
        player.stepSpeech = speech
        return SpotWalk.of(tile, (step as? OwnSpot)?.ownArea, ::walkToSpot) { turn(step) }
    }

    private fun turn(step: ResolvedStep): StepTurn =
        StepTurn(
            step.work(player, runTile()),
            amount = if (step is Counts) step.amount else null,
            needs = step.needs(),
            carried = carried,
            fillsBag = step is Gathers,
            walkBack = ::walkBack,
        )

    private fun walkToSpot(spot: Tile): StepActivity =
        StepTurn(walkBack(spot), amount = null, needs = emptyList(), carried = carried, fillsBag = false, walkBack = ::walkBack)

    private fun walkBack(to: Tile): StepWork = tripTo(player, to, Place.WORK_SPOT)

    private fun runTile(): Tile = idleState.runTile ?: tile
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
