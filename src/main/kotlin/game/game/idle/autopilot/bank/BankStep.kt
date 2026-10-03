package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * `bank nearest` or `bank @<bank>`: walks to that bank and deposits everything but the player's axes. The nearest
 * bank is picked when the step starts, from where the player stands.
 */
class BankStepType(private val catalog: BankCatalog) : StepType {

    private val ids: List<String> = catalog.banks.map { it.id }

    override val keyword = "bank"

    override val label = "bank"

    override val usage = "bank nearest|@<bank>"

    override val fields = listOf(StepField.Choice("bank") { listOf(NEAREST) + ids })

    override fun parse(words: List<String>): List<String> {
        val word = words.singleOrNull()
        return when {
            words == listOf("deposit", "all") -> throw FlowError("'bank deposit all' is now 'bank nearest' or 'bank @<bank>'")
            word == NEAREST -> listOf(NEAREST)
            word != null && word.startsWith("@") && word.length > 1 -> listOf(word.substring(1))
            else -> throw FlowError("bank takes 'nearest' or a bank: bank nearest, bank @<bank>")
        }
    }

    override fun line(values: List<String>): String = if (values[0] == NEAREST) "bank nearest" else "bank @${values[0]}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        if (values[0] == NEAREST) return BankStep(catalog.banks)
        val bank = catalog.find(values[0].lowercase())
            ?: throw FlowError("Unknown bank '${values[0]}'. Banks: ${ids.sorted().joinToString(", ")}")
        return BankStep(listOf(bank))
    }

    private companion object {
        const val NEAREST = "nearest"
    }
}

/** A bank step resolved: it uses the nearest of [candidates] when it starts. */
data class BankStep(val candidates: List<Bank>) : ResolvedStep {

    /** The candidate closest to [from] in a straight line on its floor, null when none is on that floor. */
    fun nearest(from: Tile): Bank? =
        candidates.filter { it.booth.z == from.z }.minByOrNull { squared(it.booth.x - from.x) + squared(it.booth.y - from.y) }

    override fun activity(player: Player, runTile: Tile): StepActivity =
        BankActivity(LunaBanker(player, nearest(Tile.of(player.position))?.let { it.booth.toPosition() }))

    private fun squared(value: Int): Int = value * value
}
