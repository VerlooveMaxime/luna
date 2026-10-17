package game.idle.autopilot.walk

import game.idle.autopilot.travel.tripTo
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.Place
import game.idle.flow.Reasons
import game.idle.flow.ResolvedStep
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.StepWork
import game.idle.flow.TileText
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** Walk: walks to a tile, which becomes the work spot of the action steps after it. */
object WalkStepType : StepType {

    const val TILE = "tile"

    private const val LABEL = "Tile"

    override val kind = "walk"

    override val label = "walk"

    override val description = "Walks to a tile picked on the world map."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Media("mapmarker", 0)

    /** The tile is what a walk step picks, so the slot greys "not set yet" as it does for every kind: "to 3101, 3509". */
    override fun pick(settings: StepSettings, names: GameNames): StepPick = StepPick(settings[TILE]?.let { "to ${it.replace(" ", ", ")}" }, icon = null)

    override fun fields(names: GameNames): List<StepField> = listOf(StepField.MapTile(TILE, LABEL))

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val text = settings[TILE] ?: throw FlowError(Reasons.needs("Tile"))
        return WalkStep(TileText.read(text, LABEL))
    }
}

/** A walk step resolved: the tile it goes to, the work spot of the steps after it. */
data class WalkStep(val target: Tile) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(workSpot = WorkSpot.At(target))

    override fun work(player: Player, runTile: Tile): StepWork = tripTo(player, target, Place.TILE)
}
