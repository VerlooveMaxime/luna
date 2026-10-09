package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepIcon
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.cooking.cookFood.Food
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Cook: cooks a count of the raw food the steps before it gathered or, without one, all of it, on a fire or range
 * within a radius of the work spot.
 */
object CookStepType : StepType {

    override val kind = "cook"

    override val label = "cook"

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.COOKING)

    override val fields = listOf(StepAmount.field(unbounded = "all"), StepRadius.field())

    override fun summary(settings: StepSettings): String =
        "cook ${StepAmount.prefix(settings)}".trimEnd() + StepRadius.suffix(settings)

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val raw = context.gathered intersect Food.RAW_TO_FOOD.keys
        if (raw.isEmpty()) throw FlowError("cook comes after a fish step, so the flow knows what to cook")
        return CookStep(raw, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }
}

/** A cook step resolved: the raw food it cooks, where, and how much ([amount], null for all). */
data class CookStep(val rawIds: Set<Int>, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity =
        CookingActivity(LunaCooker(player, rawIds, Area(workSpot.tile(runTile), radius)), amount)
}
