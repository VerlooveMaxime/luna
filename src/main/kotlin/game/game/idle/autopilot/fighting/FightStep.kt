package game.idle.autopilot.fighting

import game.idle.autopilot.travel.walkUpsFor
import game.idle.flow.Counts
import game.idle.flow.Fights
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.OwnSpot
import game.idle.flow.Reasons
import game.idle.flow.ResolvedStep
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepSpot
import game.idle.flow.StepType
import game.idle.flow.StepWork
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.TravelWebs
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Fight: fights the npcs of a [FightTargetCatalog] target around the work spot one at a time, a count of kills or,
 * without one, until stopped. Eating and leaving the fight are the flow's reflexes since S07c (Maxime, 2026-10-10).
 */
class FightStepType(private val catalog: FightTargetCatalog) : StepType {

    override val kind = "fight"

    override val label = "fight"

    override val description = "Fights one kind of npc, a count of kills or with no end."

    override val goal = true

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.ATTACK)

    override fun target(names: GameNames): StepTarget = StepTarget(NPC, FightOptions(catalog))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "no end", counted = "kills"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Npc", target(names), "What would you like to fight?"),
            StepAmount.field(KILLS, unbounded = "no end", button = "No end"),
            StepSpot.field(),
            StepRadius.field(),
        )

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[NPC]?.lowercase() ?: throw FlowError(Reasons.needs("Npc"))
        val target = catalog.find(name) ?: throw FlowError(Reasons.notA("Npc to fight", name))
        return FightStep(target, StepRadius.read(settings), StepSpot.of(settings, context), StepAmount.read(settings, KILLS))
    }

    companion object {
        const val NPC = "npc"

        /** The amount's row, which counts kills. */
        private const val KILLS = "Kills"
    }
}

/** A fight step resolved: [amount] kills, null for no end. */
data class FightStep(
    val target: FightTarget,
    val radius: Int,
    val workSpot: WorkSpot,
    override val amount: Int? = null,
) : ResolvedStep, OwnSpot, Counts, Fights {

    override val ownArea: Area? get() = workSpot.ownArea(radius)

    override fun after(context: FlowContext): FlowContext = workSpot.after(context.copy(fought = target.npcs))

    override fun work(player: Player, runTile: Tile): StepWork {
        val area = Area(workSpot.tile(runTile), radius)
        return FightWork(LunaFighter(player, target, area, TravelWebs.SERVER.doorwaysOf(player.world)), walkUpsFor(player))
    }
}
