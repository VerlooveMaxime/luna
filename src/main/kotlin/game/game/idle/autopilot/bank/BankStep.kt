package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import game.idle.movement.WalkingDistances
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * Bank: walks to the nearest bank or a chosen one and deposits everything but the player's tools. The nearest bank
 * is picked by walking distance when the step starts, from where the player stands.
 */
class BankStepType(private val catalog: BankCatalog) : StepType {

    private val ids: List<String> = catalog.banks.map { it.id }

    override val kind = "bank"

    override val label = "bank"

    override fun icon(settings: StepSettings): StepIcon = StepIcon.BANK

    override fun target(names: GameNames): StepTarget = StepTarget(BANK, BankOptions(catalog), default = NEAREST)

    /** It deposits all but tools (axes, pickaxes, the hammer, the tinderbox, fishing tools and bait). */
    override fun details(settings: StepSettings, context: FlowContext): List<String> = listOf("keeps tools")

    override val fields = listOf(StepField.Choice(BANK, "bank") { listOf(NEAREST) + ids })

    override fun summary(settings: StepSettings): String = "bank ${settings[BANK] ?: NEAREST}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val id = settings[BANK] ?: NEAREST
        if (id == NEAREST) return BankStep(catalog.banks)
        val bank = catalog.find(id.lowercase())
            ?: throw FlowError("Unknown bank '$id'. Banks: ${ids.sorted().joinToString(", ")}")
        return BankStep(listOf(bank))
    }

    companion object {
        const val BANK = "bank"

        /** The bank setting's word for the nearest bank, also what a bank step without one uses. */
        const val NEAREST = "nearest"
    }
}

/** The collision answers picking the nearest bank needs. [LunaBankTerrain] is the in-game one. */
interface BankTerrain {

    fun canStep(from: Position, direction: Direction): Boolean

    /** The tiles [bank]'s booth can be used from; none when no booth stands on its tile. */
    fun usableFrom(bank: Bank): List<Position>
}

/** A bank step resolved: it uses the nearest of [candidates] when it starts. */
data class BankStep(val candidates: List<Bank>) : ResolvedStep {

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

    override fun activity(player: Player, runTile: Tile): StepActivity =
        BankActivity(LunaBanker(player, nearest(player.position, LunaBankTerrain(player.world))?.booth?.toPosition()))

    private fun squared(value: Int): Int = value * value

    companion object {
        /**
         * About two minutes on foot, enough for every bank near the spots flows use; a search that finds nothing
         * this close costs the game thread up to about 200 ms.
         */
        const val MAX_WALK = 200
    }
}
