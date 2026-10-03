package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.flow.FlowGrammar
import game.idle.location.LocationCatalog

/** Every kind of step a flow can use, in the order the flow builder cycles through them. */
class IdleSteps(catalog: LocationCatalog) {

    val chop = ChopStepType(catalog)

    val grammar = FlowGrammar(listOf(chop, DropStepType, BankStepType))
}
