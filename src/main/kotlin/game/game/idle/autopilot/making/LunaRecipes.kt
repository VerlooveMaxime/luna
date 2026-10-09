package game.idle.autopilot.making

import game.idle.flow.option.GameNames
import game.obj.resource.fillable.WaterResource
import game.skill.cooking.cookFood.FermentWineTask
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import game.skill.crafting.armorCrafting.CraftArmorActionItem
import game.skill.crafting.armorCrafting.CraftStuddedActionItem
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.armorCrafting.SoftLeatherInterface
import game.skill.crafting.battlestaffCrafting.Battlestaff
import game.skill.crafting.gemCutting.Gem
import game.skill.crafting.glassMaking.GlassBlowingInterface
import game.skill.crafting.glassMaking.GlassMaterial
import game.skill.crafting.glassMaking.OilLantern
import game.skill.crafting.jewelleryMaking.JewelleryString
import game.skill.crafting.potteryCrafting.MakeSoftClayActionItem
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.cutLog.Log
import game.skill.fletching.stringBow.Bow
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.herblore.makeUnfPotion.UnfPotion
import io.luna.game.model.mob.Skill

/**
 * Every recipe the make step can make, read from Luna's item-on-item tables (Maxime, 2026-10-07: one place for the
 * data). Each table's rows become recipes with the ways, tools and window Luna's own scripts use for them.
 *
 * A product is named as the cache names it unless another item of the catalog shares that name; it then takes its
 * table's words (Maxime, 2026-10-09): unfinished potions after their herb, pie stages after the ingredient just added,
 * other food after Luna's own row name, a strung amulet "(strung)", anything else after the item it is used on.
 */
class LunaRecipes(private val names: GameNames) {

    /** A recipe named as the cache names its product, and the label it takes on a clash. */
    private data class Draft(val recipe: Recipe, val clashLabel: String)

    fun catalog(): RecipeCatalog {
        val drafts = food() + unfinishedPotions() + potions() + grinding() + logCutting() + bowStringing() + arrows() +
            gems() + leatherArmour() + studdedArmour() + glass() + oilLantern() + battlestaffs() + softClay() + stringing()
        val clashing = clashingNames(drafts.map { it.recipe })
        return RecipeCatalog(drafts.map { if (it.recipe.label in clashing) it.recipe.copy(label = it.clashLabel) else it.recipe })
    }

    /** The cache names that more than one item of the catalog has. */
    private fun clashingNames(recipes: List<Recipe>): Set<String> =
        recipes.flatMap { recipe -> recipe.ways.flatMap { listOf(it.use, it.on) + it.inputs.keys + it.tools } + recipe.product }
            .toSet()
            .groupBy(names::item)
            .filterValues { it.size > 1 }
            .keys

    private fun draft(recipe: Recipe, clashLabel: String = "${recipe.label} (${names.item(recipe.ways.first().on).lowercase()})") =
        Draft(recipe, clashLabel)

    private fun recipe(product: Int, skill: Int, level: Int, ways: List<RecipeWay>, window: MakeWindow = MakeWindow.Choice) =
        Recipe(product, names.item(product), skill, level, ways, window)

    /** A way that uses up both items. */
    private fun both(use: Int, on: Int) = RecipeWay(use, on, mapOf(use to 1, on to 1))

    /** A way where [tool] is used on [input], which alone is used up. */
    private fun withTool(tool: Int, input: Int) = RecipeWay(tool, input, mapOf(input to 1), setOf(tool))

    /** Luna's food prep, one recipe per product: the two stews made in either order are one. */
    private fun food(): List<Draft> =
        IncompleteFood.entries.groupBy { it.id }.values.map { rows ->
            val food = rows.first()
            val recipe = recipe(food.id, Skill.COOKING, food.lvl, rows.flatMap(::foodWays))
            draft(
                if (food == IncompleteFood.UNFERMENTED_WINE) recipe.copy(made = setOf(food.id, FermentWineTask.JUG_OF_WINE)) else recipe,
                foodClashLabel(food),
            )
        }

    /** The ways Luna's food prep takes for [food]: the cake tin and the pineapple's knife are kept, curry takes 3 leaves. */
    private fun foodWays(food: IncompleteFood): List<RecipeWay> =
        food.otherIngredients.map { other ->
            when (food) {
                IncompleteFood.UNCOOKED_CAKE ->
                    RecipeWay(food.baseIngredient, other, food.otherIngredients.associateWith { 1 }, setOf(food.baseIngredient))
                IncompleteFood.PINEAPPLE_RING -> withTool(other, food.baseIngredient)
                IncompleteFood.UNCOOKED_CURRY ->
                    RecipeWay(food.baseIngredient, other, mapOf(food.baseIngredient to 1, other to curryCount(other)))
                else -> both(food.baseIngredient, other)
            }
        }

    private fun curryCount(item: Int): Int = if (item == PrepareFoodActionItem.CURRY_LEAF) PrepareFoodActionItem.CURRY_LEAVES else 1

    /** A pie stage (Luna's row ends in its number) after the ingredient just added, other food after Luna's row name. */
    private fun foodClashLabel(food: IncompleteFood): String =
        if (food.name.last().isDigit()) {
            val added = food.otherIngredients.first()
            "${names.item(food.id)} (${if (added in WaterResource.FILLED_IDS) "water" else names.item(added).lowercase()})"
        } else {
            words(food.name)
        }

    private fun unfinishedPotions(): List<Draft> =
        UnfPotion.entries.map {
            draft(recipe(it.id, Skill.HERBLORE, it.level, listOf(both(UnfPotion.VIAL_OF_WATER, it.herb))), "${words(it.name)} potion (unf)")
        }

    private fun potions(): List<Draft> =
        FinishedPotion.ALL.map { draft(recipe(it.id, Skill.HERBLORE, it.level, listOf(both(it.unf, it.secondary)))) }

    /** Grinding has no level in Luna. */
    private fun grinding(): List<Draft> =
        Ingredient.entries.map { draft(recipe(it.newId, Skill.HERBLORE, 1, listOf(withTool(Ingredient.PESTLE_AND_MORTAR, it.id)))) }

    private fun logCutting(): List<Draft> =
        Log.VALUES.flatMap { log ->
            log.bows.map { draft(recipe(it.unstrung, Skill.FLETCHING, it.level, listOf(withTool(Log.KNIFE, log.id)))) }
        }

    private fun bowStringing(): List<Draft> =
        Bow.VALUES.filter { it != Bow.ARROW_SHAFT }
            .map { draft(recipe(it.strung, Skill.FLETCHING, it.level, listOf(both(Bow.BOW_STRING, it.unstrung)))) }

    /** Headless arrows are a shaft and a feather; the others a headless arrow and a tip. */
    private fun arrows(): List<Draft> =
        Arrow.VALUES.map { draft(recipe(it.id, Skill.FLETCHING, it.level, listOf(both(it.with, it.tip)))) }

    private fun gems(): List<Draft> =
        Gem.entries.map { draft(recipe(it.cut, Skill.CRAFTING, it.level, listOf(withTool(Gem.CHISEL, it.uncut)))) }

    /**
     * Armour sewn from leather with a needle and thread, so many hides at a time. Soft leather opens Luna's own window
     * of buttons; the other leathers its make window.
     */
    private fun leatherArmour(): List<Draft> =
        HideArmor.ALL.mapNotNull { armor ->
            val (hide, count) = armor.hides ?: return@mapNotNull null
            val way = RecipeWay(
                CraftArmorActionItem.NEEDLE_ID,
                hide.tan,
                mapOf(hide.tan to count, CraftArmorActionItem.THREAD_ID to 1),
                setOf(CraftArmorActionItem.NEEDLE_ID),
            )
            val window = SoftLeatherInterface.BUTTON_MAP[armor]?.let { MakeWindow.Buttons(SoftLeatherInterface::class.java, it.first) }
            draft(recipe(armor.id, Skill.CRAFTING, armor.level, listOf(way), window ?: MakeWindow.Choice))
        }

    private fun studdedArmour(): List<Draft> =
        CraftStuddedActionItem.LEATHER_TO_STUDDED.map { (leather, studded) ->
            draft(recipe(studded.id, Skill.CRAFTING, studded.level, listOf(both(CraftStuddedActionItem.STUDS, leather.id)), MakeWindow.None))
        }

    private fun glass(): List<Draft> =
        GlassMaterial.entries.map {
            val window = MakeWindow.Buttons(GlassBlowingInterface::class.java, it.make10Id)
            draft(recipe(it.id, Skill.CRAFTING, it.level, listOf(withTool(GlassMaterial.PIPE, GlassMaterial.MOLTEN_GLASS)), window))
        }

    private fun oilLantern(): List<Draft> =
        listOf(draft(recipe(OilLantern.LANTERN, Skill.CRAFTING, OilLantern.LEVEL, listOf(both(OilLantern.LAMP, OilLantern.FRAME)), MakeWindow.None)))

    private fun battlestaffs(): List<Draft> =
        Battlestaff.entries.map { draft(recipe(it.staff, Skill.CRAFTING, it.level, listOf(both(Battlestaff.BATTLESTAFF, it.orb)))) }

    /** Soft clay has no level in Luna; any water container will do. */
    private fun softClay(): List<Draft> {
        val ways = WaterResource.FILLED_IDS.map { both(MakeSoftClayActionItem.CLAY, it) }
        return listOf(draft(recipe(MakeSoftClayActionItem.SOFT_CLAY, Skill.CRAFTING, 1, ways, MakeWindow.None)))
    }

    /** Stringing has no level in Luna; an amulet keeps its name once strung. */
    private fun stringing(): List<Draft> =
        JewelleryString.UNSTRUNG_TO_STRUNG.map { (unstrung, strung) ->
            val recipe = recipe(strung, Skill.CRAFTING, 1, listOf(both(JewelleryString.BALL_OF_WOOL, unstrung)))
            draft(recipe, "${recipe.label} (strung)")
        }

    /** Luna's enum name as words: `CUP_OF_MILKY_NETTLE_TEA` reads "Cup of milky nettle tea". */
    private fun words(enumName: String): String = enumName.lowercase().replace('_', ' ').let { it.take(1).uppercase() + it.drop(1) }
}
