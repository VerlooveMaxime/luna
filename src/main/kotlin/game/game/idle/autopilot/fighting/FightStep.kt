package game.idle.autopilot.fighting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.Fights
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Fight: fights the npcs of a [FightTargetCatalog] target around the work spot one at a time, a count of kills or,
 * without one, until stopped. Eating and running away are the flow's reflexes since S07c (Maxime, 2026-10-10).
 */
class FightStepType(private val catalog: FightTargetCatalog) : StepType {

    override val kind = "fight"

    override val label = "fight"

    override val description = "Fights one kind of npc, a count of kills or with no end."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.ATTACK)

    override fun target(names: GameNames): StepTarget = StepTarget(NPC, FightOptions(catalog))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "no end", counted = "kills"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Npc", target(names), "What would you like to fight?"),
            StepAmount.field("Kills", unbounded = "no end", button = "No end"),
            StepRadius.field(),
        )

    override fun summary(settings: StepSettings): String =
        "fight ${StepAmount.prefix(settings)}${settings[NPC] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[NPC]?.lowercase() ?: throw FlowError("fight needs an npc")
        val target = catalog.find(name)
            ?: throw FlowError("'$name' is not something you can fight")
        return FightStep(target, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }

    companion object {
        const val NPC = "npc"
    }
}

/** A fight step resolved: [amount] kills, null for no end. */
data class FightStep(val target: FightTarget, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep, Fights {

    override fun after(context: FlowContext): FlowContext = context.copy(fought = target.npcs)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        FightingActivity(LunaFighter(player, target, Area(workSpot.tile(runTile), radius)), amount)
}
