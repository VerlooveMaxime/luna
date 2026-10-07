package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.cooking.CookStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.fighting.FightStepType
import game.idle.autopilot.fighting.FightTargetCatalog
import game.idle.autopilot.firemaking.LightStepType
import game.idle.autopilot.fishing.FishStepType
import game.idle.autopilot.making.MakeStepType
import game.idle.autopilot.making.RecipeCatalog
import game.idle.autopilot.mining.MineStepType
import game.idle.autopilot.smelting.SmeltStepType
import game.idle.autopilot.smithing.SmithStepType
import game.idle.autopilot.walk.WalkStepType
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.flow.StepTypes
import game.idle.location.BankCatalog

/** Every kind of step a flow can use, in the order the flow builder cycles through them. */
class IdleSteps(banks: BankCatalog, recipes: RecipeCatalog, fightTargets: FightTargetCatalog) {

    val types = StepTypes(
        listOf(
            ChopStepType, MineStepType, FishStepType, LightStepType, CookStepType, MakeStepType(recipes), SmeltStepType,
            SmithStepType, FightStepType(fightTargets), WalkStepType, DropStepType, BankStepType(banks),
        ),
    )

    companion object {
        /** The steps over the data files; a bad file fails the boot. */
        fun load(): IdleSteps =
            IdleSteps(BankCatalog.load(BankCatalog.PATH), RecipeCatalog.load(RecipeCatalog.PATH), FightTargetCatalog.load(FightTargetCatalog.PATH))
    }
}
