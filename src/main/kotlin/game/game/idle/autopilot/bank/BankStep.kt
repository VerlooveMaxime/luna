package game.idle.autopilot.bank

import game.idle.flow.BankMoves
import game.idle.flow.Choice
import game.idle.flow.FieldColumn
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepItem
import game.idle.flow.StepItems
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.ItemOptions
import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import game.idle.movement.WalkingDistances
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/** What a bank step deposits (Maxime, S01): everything in the bag, what the flow gathers or makes, or chosen items. */
enum class DepositChoice(val value: String, val word: String) {
    EVERYTHING("everything", "Everything"),
    GATHERED("gathered", "Gathered"),
    CHOSEN("chosen", "Chosen"),
}

/** What a stuck bank step does (Maxime, S01): skips itself for this lap, the default, or stops the flow. */
enum class IfStuck(val value: String, val word: String) {
    SKIP("skip", "Skip this step"),
    STOP("stop", "Stop the flow"),
}

/** Which items from the bag a bank step deposits. */
sealed interface DepositRule {

    fun takes(id: Int): Boolean

    data object Everything : DepositRule {
        override fun takes(id: Int): Boolean = true
    }

    data class Only(val ids: Set<Int>) : DepositRule {
        override fun takes(id: Int): Boolean = id in ids
    }
}

/**
 * What a bank step does at the booth: deposits by [deposit], then withdraws [withdrawals] in order, each filling the bag
 * up to its amount (as many as fit without one); [ifStuck] when it can move nothing it needed to (S07b).
 */
data class BankOrder(val deposit: DepositRule, val withdrawals: List<StepItem>, val ifStuck: IfStuck)

/**
 * Bank: walks to the nearest bank or a chosen one, deposits, then withdraws (S07b). The nearest bank is picked by
 * walking distance when the step starts, from where the player stands. Withdrawals and chosen deposits are any item of
 * [items], named with [names].
 */
class BankStepType(private val catalog: BankCatalog, private val items: ItemCatalog, private val names: GameNames) : StepType {

    private val ids: List<String> = catalog.banks.map { it.id }

    override val kind = "bank"

    override val label = "bank"

    override val description = "Deposits and withdraws at a bank."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.BANK

    override fun target(names: GameNames): StepTarget = StepTarget(BANK, BankOptions(catalog), default = NEAREST)

    /** "Deposit: gathered", then what it withdraws, if anything. */
    override fun details(settings: StepSettings, context: FlowContext): List<String> {
        val chosen = StepItems.ids(settings, DEPOSIT_ITEMS).size
        val deposit = deposit(settings).let { if (it == DepositChoice.CHOSEN) "$chosen chosen" else it.value }
        val withdrawn = StepItems.ids(settings, WITHDRAW).joinToString(", ") { names.item(it).lowercase() }
        return listOfNotNull("Deposit: $deposit", withdrawn.takeIf { it.isNotEmpty() }?.let { "Withdraw: $it" })
    }

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Bank", target(names), "Which bank?"),
            StepField.Items(
                WITHDRAW, "Withdraw", ItemOptions(items), "What would you like to withdraw?", ADD, rows = 5, amounts = true,
            ),
            StepField.Toggle(
                DEPOSIT, "Deposit", DepositChoice.entries.map { Choice(it.value, it.word) },
                current = { settings, _ -> deposit(settings).value }, column = FieldColumn.RIGHT,
            ),
            StepField.Toggle(
                STUCK, "If stuck", IfStuck.entries.map { Choice(it.value, it.word) },
                current = { settings, _ -> ifStuck(settings).value }, column = FieldColumn.RIGHT,
            ),
            StepField.Note("Banks", FieldColumn.RIGHT, visible = { deposit(it) != DepositChoice.CHOSEN }) { settings, before ->
                banks(settings, before)
            },
            StepField.Items(
                DEPOSIT_ITEMS, "Chosen", ItemOptions(items), "What would you like to deposit?", ADD, rows = 4,
                column = FieldColumn.RIGHT, visible = { deposit(it) == DepositChoice.CHOSEN },
            ),
        )

    /** "bank nearest, deposit gathered, withdraw 1 tinderbox, all oak logs". */
    override fun summary(settings: StepSettings): String {
        val withdrawals = StepItems.read(settings, WITHDRAW).joinToString(", ") { item ->
            "${item.amount ?: "all"} ${names.item(item.id).lowercase()}"
        }
        val withdraw = withdrawals.takeIf { it.isNotEmpty() }?.let { ", withdraw $it" }.orEmpty()
        return "bank ${settings[BANK] ?: NEAREST}, deposit ${deposit(settings).value}$withdraw"
    }

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val rule = when (deposit(settings)) {
            DepositChoice.EVERYTHING -> DepositRule.Everything
            DepositChoice.GATHERED -> DepositRule.Only(context.lap)
            DepositChoice.CHOSEN -> DepositRule.Only(StepItems.ids(settings, DEPOSIT_ITEMS).toSet())
        }
        val order = BankOrder(rule, StepItems.read(settings, WITHDRAW), ifStuck(settings))
        val id = settings[BANK] ?: NEAREST
        if (id == NEAREST) return BankStep(catalog.banks, order)
        val bank = catalog.find(id.lowercase())
            ?: throw FlowError("Unknown bank '$id'. Banks: ${ids.sorted().joinToString(", ")}")
        return BankStep(listOf(bank), order)
    }

    /** What Deposit's rows say when they hold no list: what Gathered banks, or that Everything takes tools too. */
    private fun banks(settings: StepSettings, before: FlowContext): String {
        if (deposit(settings) == DepositChoice.EVERYTHING) return "All the bag holds, tools too"
        val gathered = before.lap.mapIndexed { index, id -> names.item(id).let { if (index == 0) it else it.lowercase() } }
        return gathered.joinToString(", ").ifEmpty { "Nothing any step gathers yet" }
    }

    companion object {
        const val BANK = "bank"
        const val WITHDRAW = "withdraw"
        const val DEPOSIT = "deposit"
        const val DEPOSIT_ITEMS = "depositItems"
        const val STUCK = "stuck"

        /** The bank setting's word for the nearest bank, also what a bank step without one uses. */
        const val NEAREST = "nearest"

        private const val ADD = "+ Add or remove items..."

        /** The choice kept, Gathered by default (S07 plan), as for a step saved before S07b. */
        fun deposit(settings: StepSettings): DepositChoice =
            DepositChoice.entries.firstOrNull { it.value == settings[DEPOSIT] } ?: DepositChoice.GATHERED

        fun ifStuck(settings: StepSettings): IfStuck = IfStuck.entries.firstOrNull { it.value == settings[STUCK] } ?: IfStuck.SKIP
    }
}

/** The collision answers picking the nearest bank needs. [LunaBankTerrain] is the in-game one. */
interface BankTerrain {

    fun canStep(from: Position, direction: Direction): Boolean

    /** The tiles [bank]'s booth can be used from; none when no booth stands on its tile. */
    fun usableFrom(bank: Bank): List<Position>
}

/** A bank step resolved: it uses the nearest of [candidates] when it starts, and does what [order] says there. */
data class BankStep(val candidates: List<Bank>, val order: BankOrder) : ResolvedStep, BankMoves {

    override val withdraws: Set<Int> = order.withdrawals.map { it.id }.toSet()

    override fun banks(id: Int): Boolean = order.deposit.takes(id)

    /**
     * The candidate on [from]'s floor that a walk reaches first: a booth across a river can be near in a straight
     * line and far on foot. With none within [MAX_WALK] steps, the one nearest in a straight line, as a walk may
     * still get there. Null when no candidate is on that floor; a lone one is taken without a search.
     */
    fun nearest(from: Position, terrain: BankTerrain): Bank? {
        val onFloor = candidates.filter { it.booth.z == from.z }
        if (onFloor.size < 2) return onFloor.firstOrNull()
        val bankByTile = onFloor.flatMap { bank -> terrain.usableFrom(bank).map { tile -> tile to bank } }.toMap()
        val reached = WalkingDistances.firstReached(from, bankByTile.keys, MAX_WALK, terrain::canStep)
        return reached?.let(bankByTile::getValue) ?: onFloor.minBy { squared(it.booth.x - from.x) + squared(it.booth.y - from.y) }
    }

    override fun activity(player: Player, runTile: Tile): StepActivity {
        val booth = nearest(player.position, LunaBankTerrain(player.world))?.booth?.toPosition()
        return BankActivity(LunaBanker(player, booth), order, LunaGameNames)
    }

    private fun squared(value: Int): Int = value * value

    companion object {
        /**
         * About two minutes on foot, enough for every bank near the spots flows use; a search that finds nothing
         * this close costs the game thread up to about 200 ms.
         */
        const val MAX_WALK = 200
    }
}
