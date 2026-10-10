package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.cooking.CookStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.fighting.FightStepType
import game.idle.autopilot.fighting.FightTargetCatalog
import game.idle.autopilot.firemaking.LightStepType
import game.idle.autopilot.fishing.FishStepType
import game.idle.autopilot.making.LunaRecipes
import game.idle.autopilot.making.MakeStepType
import game.idle.autopilot.making.RecipeCatalog
import game.idle.autopilot.mining.MineStepType
import game.idle.autopilot.smelting.SmeltStepType
import game.idle.autopilot.smithing.SmithStepType
import game.idle.autopilot.walk.WalkStepType
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.flow.StepTypes
import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.LunaGameNames
import game.idle.location.BankCatalog

/**
 * Every kind of step a flow can use, in the order the builder's kind picker shows them, items named with [names]; a bank
 * step withdraws any of [items].
 */
class IdleSteps(banks: BankCatalog, recipes: RecipeCatalog, fightTargets: FightTargetCatalog, items: ItemCatalog, names: GameNames) {

    val types = StepTypes(
        listOf(
            ChopStepType, MineStepType, FishStepType, LightStepType(names), CookStepType(names), MakeStepType(recipes, names),
            SmeltStepType(names), SmithStepType(names), FightStepType(fightTargets), WalkStepType, DropStepType, BankStepType(banks, items, names),
        ),
    )

    companion object {
        /** The steps over the data files, the cache and Luna's tables; a bad file fails the boot. */
        fun load(): IdleSteps =
            IdleSteps(
                BankCatalog.load(BankCatalog.PATH), LunaRecipes(LunaGameNames).catalog(), FightTargetCatalog.fromCache(), ItemCatalog.fromCache(),
                LunaGameNames,
            )
    }
}
