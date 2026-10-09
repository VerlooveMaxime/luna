package game.idle.autopilot.making

import io.luna.game.model.mob.overlay.AbstractOverlay

/**
 * One way to make a recipe: [use] used on [on], carrying [inputs] (item id to count, used up) and [tools] (kept).
 */
data class RecipeWay(val use: Int, val on: Int, val inputs: Map<Int, Int>, val tools: Set<Int> = emptySet())

/** What the game asks once the two items are used together. */
sealed interface MakeWindow {

    /** Luna's make window, which offers the product among its options. */
    data object Choice : MakeWindow

    /** A window of buttons ([type]); [button] makes ten of the product. */
    data class Buttons(val type: Class<out AbstractOverlay>, val button: Int) : MakeWindow

    /** Nothing: using the items makes the product. */
    data object None : MakeWindow
}

/**
 * Something the make step can make: [product] with [level] in [skill], by any of its [ways]. [label] is its name as
 * shown, [made] the items that count as made (a wine counts until it has fermented and after).
 */
data class Recipe(
    val product: Int,
    val label: String,
    val skill: Int,
    val level: Int,
    val ways: List<RecipeWay>,
    val window: MakeWindow = MakeWindow.Choice,
    val made: Set<Int> = setOf(product),
) {
    /** What flows call it. */
    val name: String = label.lowercase()
}

/** Every recipe flows can use, by unique name ([LunaRecipes] builds them from Luna's tables). */
class RecipeCatalog(val recipes: List<Recipe>) {

    private val byName: Map<String, Recipe> = recipes.associateBy { it.name }

    init {
        val duplicates = recipes.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate recipe names: ${duplicates.sorted()}" }
    }

    fun find(name: String): Recipe? = byName[name]
}
