package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Make: makes a count of a product from [RecipeCatalog], or, without one, as many as the ingredients the player
 * carries allow. It works from the inventory, wherever the player stands. The count is of items made, in whole sets:
 * a log cut into arrow shafts makes 15 (Maxime, 2026-10-09).
 */
class MakeStepType(private val catalog: RecipeCatalog) : StepType {

    override val kind = "make"

    override val label = "make"

    override val description = "Uses one item on another: dough, potions, shafts."

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Product", target(names), "What would you like to make?"),
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
        )

    override fun summary(settings: StepSettings): String = "make ${StepAmount.prefix(settings)}${settings[PRODUCT] ?: "?"}"

    /** The recipe's skill, Crafting until one is picked (S01). */
    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(skill(settings))

    override fun skill(settings: StepSettings): Int = settings[PRODUCT]?.let { catalog.find(it.lowercase()) }?.skill ?: Skill.CRAFTING

    override fun target(names: GameNames): StepTarget = StepTarget(PRODUCT, MakeOptions(catalog))

    override fun details(settings: StepSettings, context: FlowContext): List<String> = listOf(StepAmount.detail(settings, unbounded = "all of them"))

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[PRODUCT]?.lowercase() ?: throw FlowError("make needs a product")
        val recipe = catalog.find(name) ?: throw FlowError("Nothing called '$name' can be made.")
        return MakeStep(recipe, StepAmount.read(settings))
    }

    companion object {
        const val PRODUCT = "product"
    }
}

/** A make step resolved: the recipe and how many to make ([amount], null for as many as possible). */
data class MakeStep(val recipe: Recipe, val amount: Int? = null) : ResolvedStep {

    /** Later steps can cook, drop or bank what was made. */
    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + recipe.made)

    override fun activity(player: Player, runTile: Tile): StepActivity = MakeActivity(LunaMaker(player, recipe), recipe, amount)
}
