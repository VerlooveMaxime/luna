package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * Bank: walks to the nearest bank or a chosen one and deposits everything but the player's tools. The nearest bank
 * is picked when the step starts, from where the player stands.
 */
class BankStepType(private val catalog: BankCatalog) : StepType {

    private val ids: List<String> = catalog.banks.map { it.id }

    override val kind = "bank"

    override val label = "bank"

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

/** A bank step resolved: it uses the nearest of [candidates] when it starts. */
data class BankStep(val candidates: List<Bank>) : ResolvedStep {

    /** The candidate closest to [from] in a straight line on its floor, null when none is on that floor. */
    fun nearest(from: Tile): Bank? =
        candidates.filter { it.booth.z == from.z }.minByOrNull { squared(it.booth.x - from.x) + squared(it.booth.y - from.y) }

    override fun activity(player: Player, runTile: Tile): StepActivity =
        BankActivity(LunaBanker(player, nearest(Tile.of(player.position))?.let { it.booth.toPosition() }))

    private fun squared(value: Int): Int = value * value
}
