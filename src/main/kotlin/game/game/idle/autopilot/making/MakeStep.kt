package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * Make: makes a count of a product from [RecipeCatalog], or, without one, as many as the ingredients the player
 * carries allow. It works from the inventory, wherever the player stands.
 */
class MakeStepType(private val catalog: RecipeCatalog) : StepType {

    private val names: List<String> = catalog.recipes.map { it.name }

    override val kind = "make"

    override val label = "make"

    override val fields = listOf(
        StepField.Choice(PRODUCT, "product") { names },
        StepAmount.field(unbounded = "all"),
    )

    override fun summary(settings: StepSettings): String = "make ${StepAmount.prefix(settings)}${settings[PRODUCT] ?: "?"}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[PRODUCT]?.lowercase() ?: throw FlowError("make needs a product")
        val recipe = catalog.find(name)
            ?: throw FlowError("Nothing called '$name' can be made yet. Products: ${names.sorted().joinToString(", ")}")
        return MakeStep(recipe, StepAmount.read(settings))
    }

    companion object {
        const val PRODUCT = "product"
    }
}

/** A make step resolved: the recipe and how many to make ([amount], null for as many as possible). */
data class MakeStep(val recipe: Recipe, val amount: Int? = null) : ResolvedStep {

    /** Later steps can cook, drop or bank what was made. */
    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + recipe.product)

    override fun activity(player: Player, runTile: Tile): StepActivity = MakeActivity(LunaMaker(player, recipe), recipe, amount)
}
