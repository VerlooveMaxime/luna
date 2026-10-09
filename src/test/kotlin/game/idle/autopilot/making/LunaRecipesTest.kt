package game.idle.autopilot.making

import game.idle.flow.option.LunaGameNames
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.glassMaking.GlassBlowingInterface
import game.skill.crafting.glassMaking.GlassMaterial
import game.skill.crafting.armorCrafting.SoftLeatherInterface
import game.skill.crafting.jewelleryMaking.JewelleryString
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.cutLog.Log
import game.skill.fletching.stringBow.Bow
import game.skill.herblore.makePotion.FinishedPotion
import game.skill.herblore.makeUnfPotion.UnfPotion
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/** The catalog built from Luna's tables, which name their items from the cache: these run on [TestWorld]. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LunaRecipesTest {

    private lateinit var catalog: RecipeCatalog

    @BeforeAll
    fun build() {
        TestWorld.context
        catalog = LunaRecipes(LunaGameNames).catalog()
    }

    private fun recipe(label: String): Recipe = catalog.recipes.single { it.label == label }

    private fun products(): Set<Int> = catalog.recipes.map { it.product }.toSet()

    @Test
    fun `every food Luna prepares is a recipe`() {
        val food = IncompleteFood.entries.map { it.id }.toSet()

        assertEquals(food, products().intersect(food))
    }

    @Test
    fun `the stew made in either order is one recipe, a potato stew with chicken or meat or a meat stew with a potato`() {
        assertEquals(listOf(1997 to 2140, 1997 to 2142, 1999 to 1942), recipe("Uncooked stew").ways.map { it.use to it.on })
    }

    @Test
    fun `every potion, bow, arrow and amulet of Luna's tables is a recipe`() {
        val luna = UnfPotion.entries.map { it.id } + FinishedPotion.ALL.map { it.id } + Log.VALUES.flatMap { log -> log.bows.map { it.unstrung } } +
            Bow.VALUES.filter { it != Bow.ARROW_SHAFT }.map { it.strung } + Arrow.VALUES.map { it.id } + JewelleryString.UNSTRUNG_TO_STRUNG.values

        assertEquals(emptyList<Int>(), luna.filterNot { it in products() })
    }

    @Test
    fun `a product made of several items is one recipe with a way per item it can be used on`() {
        assertEquals(5, recipe("Bread dough").ways.size)
    }

    @Test
    fun `a product is called what the cache calls it when no other item shares the name`() {
        assertEquals(listOf("Bread dough", "Attack potion(3)", "Arrow shaft", "Unblessed symbol"),
            listOf(2307, 121, 52, 1716).map { id -> catalog.recipes.single { it.product == id }.label })
    }

    @Test
    fun `unfinished potions are named after their herb`() {
        assertEquals(listOf("Guam potion (unf)", "Ranarr potion (unf)", "Dwarf weed potion (unf)"),
            listOf(91, 99, 109).map { id -> catalog.recipes.single { it.product == id }.label })
    }

    @Test
    fun `pie stages are named after the ingredient just added`() {
        assertEquals(listOf("Part mud pie (compost)", "Part mud pie (water)", "Part garden pie (tomato)", "Part garden pie (onion)"),
            listOf(7164, 7166, 7172, 7174).map { id -> catalog.recipes.single { it.product == id }.label })
    }

    @Test
    fun `other food sharing a name is named after Luna's row`() {
        assertEquals(listOf("Incomplete stew with potato", "Incomplete stew with meat", "Cup of nettle tea", "Cup of milky nettle tea", "Milky nettle tea"),
            listOf(1997, 1999, 4242, 4243, 4240).map { id -> catalog.recipes.single { it.product == id }.label })
    }

    @Test
    fun `a strung amulet, named like the unstrung one, is called strung`() {
        assertEquals("Gold amulet (strung)", catalog.recipes.single { it.product == 1692 }.label)
    }

    @Test
    fun `knives, chisels, needles, pipes and pestles are tools, not inputs`() {
        val tools = listOf("Arrow shaft", "Opal", "Leather gloves", "Vial", "Chocolate dust").map { recipe(it).ways.single().tools }

        assertEquals(listOf(setOf(946), setOf(1755), setOf(1733), setOf(1785), setOf(233)), tools)
    }

    @Test
    fun `a cake tin and a pineapple's knife are kept, curry takes three leaves`() {
        assertEquals(setOf(1944, 1927, 1933), recipe("Uncooked cake").ways.first().inputs.keys)
        assertEquals(setOf(1887), recipe("Uncooked cake").ways.first().tools)
        assertEquals(mapOf(2114 to 1), recipe("Pineapple ring").ways.single().inputs)
        assertEquals(mapOf(2001 to 1, 5970 to 3), recipe("Uncooked curry").ways.first().inputs)
    }

    @Test
    fun `leather armour takes its hides and thread`() {
        assertEquals(mapOf(6289 to 15, 1734 to 1), recipe("Snakeskin body").ways.single().inputs)
    }

    @Test
    fun `soft leather and glass answer Luna's windows of buttons, hard leather its make window`() {
        val windows = listOf("Leather gloves", "Vial", "Hardleather body").map { recipe(it).window }

        assertEquals(
            listOf(
                MakeWindow.Buttons(SoftLeatherInterface::class.java, SoftLeatherInterface.BUTTON_MAP.getValue(HideArmor.LEATHER_GLOVES).first),
                MakeWindow.Buttons(GlassBlowingInterface::class.java, GlassMaterial.VIAL.make10Id),
                MakeWindow.Choice,
            ),
            windows,
        )
    }

    @Test
    fun `studded armour, the oil lantern and soft clay are made without a window`() {
        assertEquals(List(4) { MakeWindow.None }, listOf("Studded body", "Studded chaps", "Oil lantern", "Soft clay").map { recipe(it).window })
    }

    @Test
    fun `soft clay can be made with any water container`() {
        assertEquals(5, recipe("Soft clay").ways.size)
    }

    @Test
    fun `wine counts as made until it has fermented and after`() {
        assertEquals(setOf(1995, 1993), recipe("Unfermented wine").made)
    }

    @Test
    fun `each recipe keeps the skill and level of its table`() {
        val levels = listOf("Bread dough", "Ranarr potion (unf)", "Magic longbow", "Soft clay", "Dragonstone").map { recipe(it).let { r -> r.skill to r.level } }

        assertEquals(listOf(Skill.COOKING to 1, Skill.HERBLORE to 30, Skill.FLETCHING to 85, Skill.CRAFTING to 1, Skill.CRAFTING to 55), levels)
    }
}
