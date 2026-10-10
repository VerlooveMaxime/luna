package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ProcessInput
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepNeeds
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.ToolNeed
import game.idle.flow.Uses
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.StepTarget
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Make: makes a count of a product from [RecipeCatalog], or, without one, as many as the ingredients the player
 * carries allow. It works from the inventory, wherever the player stands. The count is of items made, in whole sets:
 * a log cut into arrow shafts makes 15 (Maxime, 2026-10-09). The ingredients come from earlier steps or the bank
 * (S07a), named with [names].
 */
class MakeStepType(private val catalog: RecipeCatalog, private val names: GameNames) : StepType {

    private val ingredients: Set<Int> = catalog.recipes.flatMap { recipe -> recipe.ways.flatMap { it.inputs.keys } }.toSet()

    override val kind = "make"

    override val label = "make"

    override val description = "Uses one item on another (dough, potions, shafts), from earlier steps or the bank."

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(ingredients),
            StepField.Search("Product", target(names), "What would you like to make?"),
            StepField.Note("Uses") { settings, _ -> uses(settings) },
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
        )

    override fun summary(settings: StepSettings): String = "make ${StepAmount.prefix(settings)}${settings[PRODUCT] ?: "?"}"

    /** The recipe's skill, Crafting until one is picked (S01). */
    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(skill(settings))

    override fun skill(settings: StepSettings): Int = recipe(settings)?.skill ?: Skill.CRAFTING

    override fun target(names: GameNames): StepTarget = StepTarget(PRODUCT, MakeOptions(catalog))

    override fun input(settings: StepSettings, before: FlowContext): InputSource = ProcessInput.read(settings, before, ingredients)

    override fun newSettings(before: FlowContext): StepSettings = ProcessInput.initial(StepSettings(kind), before, ingredients)

    override fun details(settings: StepSettings, context: FlowContext): List<String> {
        val inputs = recipe(settings)?.ways?.flatMap { it.inputs.keys }.orEmpty().toSet()
        return listOf(
            StepInput.detail("Ingredients", input(settings, context), context, inputs),
            StepAmount.detail(settings, unbounded = "all of them"),
        )
    }

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[PRODUCT]?.lowercase() ?: throw FlowError("make needs a product")
        val recipe = catalog.find(name) ?: throw FlowError("Nothing called '$name' can be made.")
        // One way taking only what the steps before get is enough; else the first way names what is missing.
        val way = recipe.ways.firstOrNull { context.gathered.containsAll(it.inputs.keys) } ?: recipe.ways.first()
        ProcessInput.requireGathered(input(settings, context), context, way.inputs.keys.toList(), names)
        return MakeStep(recipe, StepAmount.read(settings))
    }

    /** The first way's items, "or another way" when there are more (S03c: one row per product). */
    private fun uses(settings: StepSettings): String {
        val ways = recipe(settings)?.ways ?: return Uses.NOTHING_PICKED
        return Uses.text(ways.first().inputs, names) + if (ways.size > 1) " (or another way)" else ""
    }

    private fun recipe(settings: StepSettings): Recipe? = settings[PRODUCT]?.let { catalog.find(it.lowercase()) }

    companion object {
        const val PRODUCT = "product"
    }
}

/** A make step resolved: the recipe and how many to make ([amount], null for as many as possible). */
data class MakeStep(val recipe: Recipe, val amount: Int? = null) : ResolvedStep {

    /** Later steps can cook, drop or bank what was made. */
    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + recipe.made)

    /** One entry per way to make the recipe: its tools and what it uses up. */
    override fun needs(): List<StepNeeds> =
        recipe.ways.map { way -> StepNeeds(way.tools.map { ToolNeed(word = null, mapOf(it to 1)) }, way.inputs.keys.toList()) }

    override fun activity(player: Player, runTile: Tile): StepActivity = MakeActivity(LunaMaker(player, recipe), recipe, amount)
}
