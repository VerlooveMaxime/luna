package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.cooking.cookFood.Food
import io.luna.game.model.mob.Player

/**
 * `cook [<n>] [within <r>]`: cooks n of the raw food the steps before it gathered or, without n, all of it, on a fire
 * or range within r tiles of the work spot.
 */
object CookStepType : StepType {

    /** The amount field's word for "all the raw food". */
    const val ALL = "all"

    override val keyword = "cook"

    override val label = "cook"

    override val usage = "cook [<n>] [within <r>]"

    override val fields = listOf(
        StepField.Choice("amount") { listOf(ALL) + StepAmount.COUNTS },
        StepRadius.field(),
    )

    override fun parse(words: List<String>): List<String> {
        val (count, rest) = StepAmount.split(words)
        val radius = StepRadius.parse(rest, after = "count", usage)
        return listOf(StepAmount.value(count, ALL), radius.toString())
    }

    override fun line(values: List<String>): String =
        "cook ${StepAmount.prefix(values[AMOUNT])}".trimEnd() + StepRadius.suffix(values[RADIUS])

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val raw = context.gathered intersect Food.RAW_TO_FOOD.keys
        if (raw.isEmpty()) throw FlowError("cook comes after a fish step, so the flow knows what to cook")
        return CookStep(raw, StepRadius.check(values[RADIUS]), context.workSpot, StepAmount.count(values[AMOUNT]))
    }

    private const val AMOUNT = 0
    private const val RADIUS = 1
}

/** A cook step resolved: the raw food it cooks, where, and how much ([amount], null for all). */
data class CookStep(val rawIds: Set<Int>, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity =
        CookingActivity(LunaCooker(player, rawIds, Area(workSpot.tile(runTile), radius)), amount)
}
