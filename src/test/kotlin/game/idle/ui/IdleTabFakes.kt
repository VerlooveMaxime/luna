package game.idle.ui

import game.idle.flow.FakeStepType
import game.idle.flow.FlowResolver
import game.idle.flow.SavedFlows
import game.idle.flow.StepLines
import game.idle.flow.StepType
import game.idle.flow.StepTypes
import game.idle.flow.option.FakeNames
import game.idle.font.EVEN_FONT

/** The Idle tab for [slots] saved-flow slots, its texts measured in [EVEN_FONT]. */
fun idleTab(slots: Int = 2): IdleTab = IdleTab(SavedFlows(slots), EVEN_FONT, EVEN_FONT)

/** The overlay's lines over [types] (a fake chop and drop without any), measured in [EVEN_FONT]. */
fun status(vararg types: StepType): AutopilotStatus {
    val kinds = types.toList().ifEmpty { listOf(FakeStepType("chop"), FakeStepType("drop")) }
    return AutopilotStatus(StepLines(FlowResolver(StepTypes(kinds)), FakeNames()), EVEN_FONT)
}
