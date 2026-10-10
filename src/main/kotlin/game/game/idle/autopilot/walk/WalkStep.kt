package game.idle.autopilot.walk

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.TileText
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** Walk: walks to a tile, which becomes the work spot of the action steps after it. */
object WalkStepType : StepType {

    const val TILE = "tile"

    override val kind = "walk"

    override val label = "walk"

    override val description = "Walks to a tile picked on the world map."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Media("mapmarker", 0)

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(settings[TILE]?.let { "to ${it.replace(" ", ", ")}" } ?: "not set yet")

    override fun fields(names: GameNames): List<StepField> = listOf(StepField.MapTile(TILE, "Tile"))

    override fun summary(settings: StepSettings): String = "walk ${settings[TILE] ?: "?"}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val text = settings[TILE] ?: throw FlowError("walk needs a tile")
        return WalkStep(TileText.read(text, "walk"))
    }
}

/** A walk step resolved: the tile it goes to, the work spot of the steps after it. */
data class WalkStep(val target: Tile) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(workSpot = WorkSpot.At(target))

    override fun activity(player: Player, runTile: Tile): StepActivity = WalkActivity(LunaWalker(player, target), target)
}
