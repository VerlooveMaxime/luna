package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * `make [<n>] <product>`: makes n of a product from [RecipeCatalog], or, without n, as many as the ingredients the
 * player carries allow. It works from the inventory, wherever the player stands.
 */
class MakeStepType(private val catalog: RecipeCatalog) : StepType {

    private val names: List<String> = catalog.recipes.map { it.name }

    override val keyword = "make"

    override val label = "make"

    override val usage = "make [<n>] <product>"

    override val fields = listOf(
        StepField.Choice("product") { names },
        StepField.Choice("amount") { listOf(ALL) + StepAmount.COUNTS },
    )

    override fun parse(words: List<String>): List<String> {
        val (count, rest) = StepAmount.split(words)
        if (rest.isEmpty()) throw FlowError("make needs a product: $usage")
        return listOf(rest.joinToString(" "), StepAmount.value(count, ALL))
    }

    override fun line(values: List<String>): String = "make ${StepAmount.prefix(values[AMOUNT])}${values[PRODUCT]}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val name = values[PRODUCT].lowercase()
        val recipe = catalog.find(name)
            ?: throw FlowError("Nothing called '$name' can be made yet. Products: ${names.sorted().joinToString(", ")}")
        return MakeStep(recipe, StepAmount.count(values[AMOUNT]))
    }

    private companion object {
        /** The amount field's word for "as many as the ingredients allow". */
        const val ALL = "all"
        const val PRODUCT = 0
        const val AMOUNT = 1
    }
}

/** A make step resolved: the recipe and how many to make ([amount], null for as many as possible). */
data class MakeStep(val recipe: Recipe, val amount: Int? = null) : ResolvedStep {

    /** Later steps can cook, drop or bank what was made. */
    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + recipe.product)

    override fun activity(player: Player, runTile: Tile): StepActivity = MakeActivity(LunaMaker(player, recipe), recipe, amount)
}
