package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.OwnSpot
import game.idle.flow.ProcessInput
import game.idle.flow.ProcessKinds
import game.idle.flow.LegacyKind
import game.idle.flow.LegacyActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepNeeds
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.StepSpot
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.firemaking.Log
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Light: lights a count of the kinds of logs picked or, without one, all of them, where the player stands or from a tile
 * picked for it (S07e). The logs come from earlier steps or the bank (S07a), named with [names].
 */
class LightStepType(private val names: GameNames) : StepType {

    private val logs = ProcessKinds(LOGS, kind = "light", what = "logs", usable = Log.entries.map { it.id }.toSet(), names)

    override val kind = "light"

    override val label = "light"

    override val description = "Lights the logs picked where it stands or at its work spot, from earlier steps or the bank."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.FIREMAKING)

    override fun skill(settings: StepSettings): Int = Skill.FIREMAKING

    override fun pick(settings: StepSettings, names: GameNames): StepPick = logs.pick(settings)

    override fun input(settings: StepSettings, before: FlowContext): InputSource = ProcessInput.read(settings, before, logs.usable)

    override fun newSettings(before: FlowContext): StepSettings {
        val settings = ProcessInput.initial(StepSettings(kind), before, logs.usable)
        return logs.initial(settings, before, input(settings, before))
    }

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(
            StepInput.detail("Logs", input(settings, context), context, logs.ids(settings).toSet()),
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(logs.usable),
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
            logs.field("Logs", LogOptions(names), "Which logs would you like to light?"),
            StepSpot.field(),
        )

    override fun summary(settings: StepSettings): String =
        "light ${StepAmount.prefix(settings)}${logs.ids(settings).joinToString(", ") { names.item(it).lowercase() }}".trimEnd() +
            StepSpot.suffix(settings)

    override fun resolve(settings: StepSettings, context: FlowContext): LegacyKind =
        LightStep(logs.resolve(settings, context, input(settings, context)), StepAmount.read(settings), StepSpot.read(settings))

    companion object {
        const val LOGS = "logs"
    }
}

/**
 * A light step resolved: the logs it lights, how many ([amount], null for all), and the tile picked for it ([spot]), its
 * fires starting there, or null to light where it stands, not at the flow's spot (Maxime, 2026-10-10). A picked tile is
 * where the flow went for the steps after it.
 */
data class LightStep(val logIds: Set<Int>, val amount: Int? = null, val spot: Tile? = null) : LegacyKind, OwnSpot {

    /** Light has no Within: the player walks to the tile unless standing on it. */
    override val ownArea: Area? get() = spot?.let { Area(it, radius = 0) }

    override fun after(context: FlowContext): FlowContext = spot?.let { WorkSpot.Own(it).after(context) } ?: context

    override fun needs(): List<StepNeeds> =
        listOf(StepNeeds(tools = listOf(LightPlanner.TINDERBOX), inputs = logIds.toList()))

    override fun activity(player: Player, runTile: Tile): LegacyActivity =
        LightActivity(LunaLighter(player, logIds, spot?.toPosition() ?: player.position), amount)
}
