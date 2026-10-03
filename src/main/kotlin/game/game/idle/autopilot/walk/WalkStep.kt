package game.idle.autopilot.walk

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** `walk <x> <y> [<floor>]`: walks to a tile, which becomes the work spot of the action steps after it. */
object WalkStepType : StepType {

    override val keyword = "walk"

    override val label = "walk"

    override val usage = "walk <x> <y>"

    override val fields = listOf(StepField.MapTile("to (click: your tile)"))

    override fun parse(words: List<String>): List<String> = listOf(tile(words).text())

    override fun line(values: List<String>): String = "walk ${values[0]}"

    /** The value is checked again: the builder writes it, not the parser. */
    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep =
        WalkStep(tile(values[0].split(" ").filter { it.isNotEmpty() }))

    private fun tile(words: List<String>): Tile {
        val numbers = words.mapNotNull { it.toIntOrNull() }
        if (words.size !in 2..3 || numbers.size != words.size) {
            throw FlowError("walk needs a tile: walk <x> <y>, or walk <x> <y> <floor>")
        }
        val (x, y) = numbers
        val z = numbers.getOrElse(2) { 0 }
        if (x < 0 || y < 0) throw FlowError("A tile has no negative coordinates: walk $x $y")
        if (z !in 0..3) throw FlowError("The floor is 0 to 3, not $z")
        return Tile(x, y, z)
    }
}

/** A walk step resolved: the tile it goes to, the work spot of the steps after it. */
data class WalkStep(val target: Tile) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(workSpot = WorkSpot.At(target))

    override fun activity(player: Player, runTile: Tile): StepActivity = WalkActivity(LunaWalker(player, target), target)
}
