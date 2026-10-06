package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.cooking.CookStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.firemaking.LightStepType
import game.idle.autopilot.fishing.FishStepType
import game.idle.autopilot.making.MakeStepType
import game.idle.autopilot.making.RecipeCatalog
import game.idle.autopilot.walk.WalkStepType
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.flow.FlowGrammar
import game.idle.location.BankCatalog

/** Every kind of step a flow can use, in the order the flow builder cycles through them. */
class IdleSteps(banks: BankCatalog, recipes: RecipeCatalog) {

    val grammar = FlowGrammar(
        listOf(
            ChopStepType, FishStepType, LightStepType, CookStepType, MakeStepType(recipes), WalkStepType, DropStepType,
            BankStepType(banks),
        ),
    )
}
