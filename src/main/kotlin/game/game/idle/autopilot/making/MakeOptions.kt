package game.idle.autopilot.making

import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.ProcessOptions
import game.idle.flow.option.StepOption

/**
 * Everything a make step can make, one row per product (Maxime, 2026-10-09), greyed below its level. From earlier
 * steps a product is offered when one of its ways takes only what they get (tools are never gathered: arrow shafts
 * after a chop step); from the bank its row counts the way the bank holds the most of.
 */
class MakeOptions(private val catalog: RecipeCatalog) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> = catalog.recipes.mapNotNull { option(context, it) }

    private fun option(context: OptionContext, recipe: Recipe): StepOption? {
        val way = if (context.input == InputSource.BANK) mostBanked(context, recipe) else gathered(context, recipe)
        val inputs = way?.inputs?.keys?.toList() ?: return null
        return ProcessOptions.option(context, recipe.product, recipe.label, inputs, recipe.skill, recipe.level, value = recipe.name)
    }

    private fun gathered(context: OptionContext, recipe: Recipe): RecipeWay? =
        recipe.ways.firstOrNull { context.before.gathered.containsAll(it.inputs.keys) }

    private fun mostBanked(context: OptionContext, recipe: Recipe): RecipeWay =
        recipe.ways.maxWith(compareBy { way -> way.inputs.keys.map(context.facts::banked).min() })
}
