package game.idle.autopilot.rest

import game.idle.autopilot.reflex.LunaReflexBody
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.StepWork
import game.idle.flow.Works
import game.idle.flow.option.GameNames
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/** Rest (S01, built in S07f): waits where it stands until hitpoints are back up to a share of full, 100% by default. */
object RestStepType : StepType {

    const val HITPOINTS = "hitpoints"

    const val FULL = 100

    val RANGE = 1..FULL

    const val RULE = "rest takes 1 to 100 percent"

    override val kind = "rest"

    override val label = "rest"

    override val description = "Waits where it stands until hitpoints are back up to a share of full."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.HITPOINTS)

    override fun details(settings: StepSettings, context: FlowContext): List<String> = listOf("until ${shown(settings[HITPOINTS])}")

    override fun fields(names: GameNames): List<StepField> = listOf(StepField.Typed(HITPOINTS, "Until", RANGE, RULE, shown = ::shown))

    override fun summary(settings: StepSettings): String = "rest until ${shown(settings[HITPOINTS])}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep = RestStep(percent(settings))

    /** The percent kept, [FULL] without one; throws [FlowError] for one out of [RANGE]. */
    private fun percent(settings: StepSettings): Int {
        val text = settings[HITPOINTS] ?: return FULL
        val percent = text.toIntOrNull()
        if (percent == null || percent !in RANGE) throw FlowError("$RULE, not '$text'")
        return percent
    }

    private fun shown(percent: String?): String = "${percent ?: FULL}% hitpoints"
}

/** A rest step resolved: it waits until hitpoints are at least [percent] of full. */
data class RestStep(val percent: Int) : Works {

    override fun work(player: Player, runTile: Tile): StepWork = RestWork(LunaReflexBody(player)::health, percent)
}
