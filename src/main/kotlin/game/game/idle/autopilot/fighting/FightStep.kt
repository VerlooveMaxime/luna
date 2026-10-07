package game.idle.autopilot.fighting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * When the fight step eats: `eat below <p>%` at the end of its line, a share of the player's full hitpoints, so a
 * flow keeps working after a reset brings hitpoints back to 10.
 */
object EatBelow {

    const val DEFAULT = 50

    val CHOICES = listOf(25, 50, 75).map(::text)

    fun field(): StepField.Choice = StepField.Choice("eat below", default = text(DEFAULT)) { CHOICES }

    fun text(percent: Int): String = "$percent%"

    /** The share the last words of a line give: none for the default, or `eat below <p>%`; [usage] goes in the error. */
    fun parse(words: List<String>, usage: String): Int = when {
        words.isEmpty() -> DEFAULT
        words.size == 3 && words[0] == "eat" && words[1] == "below" -> check(words[2])
        else -> throw FlowError("Unexpected '${words.joinToString(" ")}' in the fight step: $usage")
    }

    fun check(text: String): Int {
        val percent = text.removeSuffix("%").toIntOrNull()
        if (!text.endsWith("%") || percent == null || percent !in 1..99) {
            throw FlowError("eat below takes a share of your hitpoints from 1% to 99%, not '$text'")
        }
        return percent
    }

    /** The end of a line for the field value: nothing for the default. */
    fun suffix(value: String): String = if (value == text(DEFAULT)) "" else " eat below $value"
}

/**
 * `fight [<n>] <npc> [within <r>] [eat below <p>%]`: fights npcs from [FightTargetCatalog] around the work spot one
 * at a time, n kills or, without n, until stopped. Food in the inventory is eaten once hitpoints fall below p % of
 * full; with none left the player runs from what attacks them and the flow stops.
 */
class FightStepType(private val catalog: FightTargetCatalog) : StepType {

    private val names: List<String> = catalog.targets.map { it.name }

    override val keyword = "fight"

    override val label = "fight"

    override val usage = "fight [<n>] <npc> [within <r>] [eat below <p>%]"

    override val fields = listOf(
        StepField.Choice("npc") { names },
        StepField.Choice("amount") { listOf(NONSTOP) + StepAmount.COUNTS },
        StepRadius.field(),
        EatBelow.field(),
    )

    override fun parse(words: List<String>): List<String> {
        val (count, rest) = StepAmount.split(words)
        val eatAt = rest.indexOf("eat").takeIf { it >= 0 } ?: rest.size
        val withinAt = rest.indexOf("within").takeIf { it in 0 until eatAt } ?: eatAt
        if (withinAt == 0) throw FlowError("fight needs an npc: $usage")
        val radius = StepRadius.parse(rest.subList(withinAt, eatAt), after = "npc", usage)
        val eatBelow = EatBelow.parse(rest.subList(eatAt, rest.size), usage)
        return listOf(rest.subList(0, withinAt).joinToString(" "), StepAmount.value(count, NONSTOP), radius.toString(), EatBelow.text(eatBelow))
    }

    override fun line(values: List<String>): String =
        "fight ${StepAmount.prefix(values[AMOUNT])}${values[NPC]}${StepRadius.suffix(values[RADIUS])}${EatBelow.suffix(values[EAT])}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val name = values[NPC].lowercase()
        val target = catalog.find(name)
            ?: throw FlowError("'$name' is not something you can fight yet. Fight: ${names.sorted().joinToString(", ")}")
        return FightStep(target, StepRadius.check(values[RADIUS]), context.workSpot, StepAmount.count(values[AMOUNT]), EatBelow.check(values[EAT]))
    }

    private companion object {
        /** The amount field's word for "until the flow is stopped". */
        const val NONSTOP = "nonstop"
        const val NPC = 0
        const val AMOUNT = 1
        const val RADIUS = 2
        const val EAT = 3
    }
}

/** A fight step resolved: [amount] kills (null for no end), eating below [eatBelow] % of full hitpoints. */
data class FightStep(
    val target: FightTarget,
    val radius: Int,
    val workSpot: WorkSpot,
    val amount: Int? = null,
    val eatBelow: Int = EatBelow.DEFAULT,
) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity =
        FightingActivity(LunaFighter(player, target, Area(workSpot.tile(runTile), radius)), eatBelow, amount)
}
