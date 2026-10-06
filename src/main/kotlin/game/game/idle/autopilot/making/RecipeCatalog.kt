package game.idle.autopilot.making

import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Something the make step can make: [product] by using item [use] on item [on]. */
data class Recipe(val product: Int, val name: String, val use: Int, val on: Int)

/** Every recipe flows can use, from [PATH]. Loaded once at boot; a bad file fails the boot. */
class RecipeCatalog(val recipes: List<Recipe>) {

    private val byName: Map<String, Recipe> = recipes.associateBy { it.name }

    init {
        val duplicates = recipes.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate recipe names: ${duplicates.sorted()}" }
    }

    fun find(name: String): Recipe? = byName[name]

    companion object {
        val PATH: Path = Paths.get("data", "idle", "recipes.jsonc")

        fun parse(jsonc: String): RecipeCatalog {
            val file = GsonUtils.GSON.fromJson(jsonc, RecipesJson::class.java) ?: RecipesJson()
            return RecipeCatalog(file.recipes.map { it.toRecipe() })
        }

        fun load(path: Path): RecipeCatalog = parse(Files.readString(path))
    }
}

/* Raw Gson shapes: every field has a default, so a missing key becomes a message naming the recipe. */

internal data class RecipesJson(val recipes: List<RecipeJson> = emptyList())

internal data class RecipeJson(val product: Int = -1, val name: String = "", val use: Int = -1, val on: Int = -1) {

    fun toRecipe(): Recipe {
        require(name.isNotBlank()) { "A recipe has no name: $this" }
        require(name == name.lowercase().trim()) { "Recipe '$name' must be named in lower case" }
        require(product >= 0) { "Recipe '$name' has no product" }
        require(use >= 0) { "Recipe '$name' has no item to use" }
        require(on >= 0) { "Recipe '$name' has no item to use it on" }
        return Recipe(product, name, use, on)
    }
}
