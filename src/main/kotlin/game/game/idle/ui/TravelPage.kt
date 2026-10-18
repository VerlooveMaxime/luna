package game.idle.ui

import game.content.sailing.ShipRoute
import game.idle.IdleState
import game.idle.flow.StepWords
import game.idle.flow.TeleportDestination
import game.idle.flow.Teleports
import game.idle.flow.capitalised
import game.idle.flow.option.GameNames
import game.idle.font.ClientFont
import game.idle.movement.TravelChoice
import game.npc.spawn.alKharid.BorderGate
import io.luna.game.model.Position
import kotlin.math.abs

/** One row of the Travel tab as the player sees it: its [choice], whether the flow [allowed] it, and the [lines] under it. */
data class TravelRowView(val choice: TravelChoice, val allowed: Boolean, val lines: List<String>)

/**
 * The builder's Travel tab (web-walker movement Part 3b, Maxime 2026-10-18): a row per [TravelChoice], named as the
 * long-way line names it, with No lit until the flow allows the choice; under each a grey note on what it spends, read
 * from Luna ([toll], [ships], [teleports]), then in white what the flow's teleport reflexes keep back from it, one
 * summed line (Q2's reserve: a spell's runes that no worn item stands for, a charge per jewellery teleport); last, the
 * grey line on what is always used. Lines are fitted to the client's small [font].
 */
class TravelPage(
    private val names: GameNames,
    private val font: ClientFont,
    private val toll: Int = BorderGate.TOLL,
    private val ships: List<ShipRoute> = ShipRoute.entries,
    private val teleports: List<TeleportDestination> = Teleports.all,
) {

    /** The rows for [state], the kept-back runes worked out with the items the player has [worn]. */
    fun rows(state: IdleState, worn: Set<Int>): List<TravelRowView> {
        val keeping = Teleports.reserving(state.steps, state.reflexes)
        return TravelChoice.entries.map { choice ->
            val notes = font.wrap(note(choice), BuilderWidgets.TRAVEL_LINE_ROOM, BuilderWidgets.TRAVEL_LINES).map { "@gry@$it" }
            val kept = kept(keeping.filterValues { choiceOf(it) == choice }, worn)?.let { line ->
                font.wrap(line, BuilderWidgets.TRAVEL_LINE_ROOM, BuilderWidgets.TRAVEL_LINES).map { "@whi@$it" }
            }
            TravelRowView(choice, state.travel.allows(choice), (notes + kept.orEmpty()).take(BuilderWidgets.TRAVEL_LINES))
        }
    }

    /** Every widget of the page: each row's name, its toggle lit as the flow has it, its lines, then the always-used line. */
    fun updates(state: IdleState, worn: Set<Int>): List<WidgetUpdate> =
        rows(state, worn).withIndex().flatMap { (row, view) ->
            listOf(
                WidgetUpdate.Text(BuilderWidgets.travelLabel(row), capitalised(StepWords.travelRow(view.choice))),
                WidgetUpdate.Text(BuilderWidgets.travelNo(row), lit(NO, !view.allowed)),
                WidgetUpdate.Colour(BuilderWidgets.travelNoFrame(row), frame(lit = !view.allowed)),
                WidgetUpdate.Text(BuilderWidgets.travelYes(row), lit(yes(view.choice), view.allowed)),
                WidgetUpdate.Colour(BuilderWidgets.travelYesFrame(row), frame(lit = view.allowed)),
            ) + (0 until BuilderWidgets.TRAVEL_LINES).map { WidgetUpdate.Text(BuilderWidgets.travelLine(row, it), view.lines.getOrNull(it).orEmpty()) }
        } + WidgetUpdate.Text(BuilderWidgets.TRAVEL_ALWAYS, "@gry@${font.fit(ALWAYS_USED, BuilderWidgets.TRAVEL_ALWAYS_ROOM)}")

    /** What [choice] spends on, as its grey note says it. */
    fun note(choice: TravelChoice): String =
        when (choice) {
            TravelChoice.COINS -> tolls()
            TravelChoice.RUNES -> "${teleports.count { it is TeleportDestination.Spell }} spells, with the runes carried"
            TravelChoice.CHARGES -> "${teleports.count { it is TeleportDestination.Jewellery }} destinations, with jewellery carried"
        }

    /**
     * "Al Kharid toll gate 10 coins; ships between Port Sarim and Karamja, Ardougne and Brimhaven 30 coins" (Maxime,
     * 2026-10-18): each pair of ports a paid ship sails between, once, grouped by fare.
     */
    private fun tolls(): String {
        val pairs = ships.filter { it.fare > 0 }.map { setOf(portOf(it), it.place) to it.fare }.distinctBy { it.first }
        val byFare = pairs.groupBy({ it.second }, { it.first.joinToString(" and ") })
        return "Al Kharid toll gate $toll coins" + byFare.entries.joinToString("") { (fare, between) -> "; ships between ${between.joinToString(", ")} $fare coins" }
    }

    /** The port [route] leaves from, which Luna does not name: the place of the route arriving nearest its departure. */
    private fun portOf(route: ShipRoute): String = ships.minBy { distance(it.destination, route.departure) }.place

    private fun distance(one: Position, other: Position): Int = maxOf(abs(one.x - other.x), abs(one.y - other.y))

    /**
     * What the reflexes in [keeping] (their index to where they teleport) keep back, null for none: "Kept for reflex 2's
     * Varrock teleport: 3 air, 1 fire, 1 law", "Kept for reflexes 2 and 3's teleports: 6 air, 1 fire, 2 law, 1 water",
     * "Kept for reflex 4's Edgeville teleport: 1 amulet of glory charge".
     */
    private fun kept(keeping: Map<Int, TeleportDestination>, worn: Set<Int>): String? {
        if (keeping.isEmpty()) return null
        val numbers = keeping.keys.map { it + 1 }
        val whose = if (numbers.size == 1) "reflex ${numbers.single()}'s" else "reflexes ${numbers.dropLast(1).joinToString(", ")} and ${numbers.last()}'s"
        val destinations = keeping.values.distinct()
        val what = destinations.singleOrNull()?.let { "${Teleports.named(it).removePrefix("the ")} teleport" } ?: "teleports"
        val runes = destinations.filterIsInstance<TeleportDestination.Spell>().map { Teleports.listed(it.spell.requirements, worn) }
            .fold(emptyMap<Int, Int>()) { total, next -> total + next.mapValues { (id, amount) -> (total[id] ?: 0) + amount } }
        val charges = destinations.filterIsInstance<TeleportDestination.Jewellery>().groupingBy { it.jewellery }.eachCount().entries
            .joinToString(", ") { (jewellery, count) -> "$count ${Teleports.jewelleryName(jewellery, names)} charge${if (count == 1) "" else "s"}" }
        val amounts = listOf(Teleports.amounts(runes, names), charges).filter { it.isNotEmpty() }.joinToString(", ")
        return "Kept for $whose $what: $amounts"
    }

    private fun choiceOf(destination: TeleportDestination): TravelChoice =
        when (destination) {
            is TeleportDestination.Spell -> TravelChoice.RUNES
            is TeleportDestination.Jewellery -> TravelChoice.CHARGES
        }

    private fun lit(text: String, on: Boolean): String = if (on) "@whi@$text" else text

    private fun frame(lit: Boolean): Int = if (lit) BuilderWidgets.LIT else BuilderWidgets.UNLIT

    companion object {
        const val NO = "No"

        /** Free passages need no choice (Q1, Maxime 2026-10-17); free ships said too (Maxime, 2026-10-18). */
        const val ALWAYS_USED = "Doors, gates, stairs, ladders, trapdoors and free ships are always used."

        /** The button that allows [choice], beside No. */
        fun yes(choice: TravelChoice): String =
            when (choice) {
                TravelChoice.COINS -> "Pay coins"
                TravelChoice.RUNES -> "Use runes"
                TravelChoice.CHARGES -> "Use charges"
            }
    }
}
