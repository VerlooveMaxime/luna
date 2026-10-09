package game.idle.ui

import game.idle.autopilot.fighting.FightTargetCatalog
import game.idle.autopilot.fishing.FishingMethod
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.OptionFacts
import game.idle.location.BankCatalog
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GallerySearchesTest {

    private fun searches() = GallerySearches.all(
        LunaGameNames,
        BankCatalog.load(BankCatalog.PATH),
        FightTargetCatalog.fromCache(),
        ItemCatalog.fromCache(),
    ) { OptionFacts(mapOf(Skill.WOODCUTTING to 15)) }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("gallery", Position(3200, 3200))

    @Test
    fun `there is a search per gallery button, in the buttons' order`() {
        login()

        assertEquals(
            listOf(
                "Which tree would you like to cut?", "What would you like to fish?", "Which skill?", "Which bank?",
                "What would you like to fight?", "Which item?",
            ),
            searches().map { it.title },
        )
    }

    @Test
    fun `trees are judged on the player's facts`() {
        val trees = searches()[0].options(login())

        assertEquals(listOf(null, "needs Woodcutting 30"), listOf("oak", "willow").map { tree -> trees.single { it.value == tree }.blocked })
    }

    @Test
    fun `fishing lists every method`() {
        assertEquals(FishingMethod.ALL.size, searches()[1].options(login()).size)
    }

    @Test
    fun `skills list every skill`() {
        assertEquals(Skill.NAMES.size, searches()[2].options(login()).size)
    }

    @Test
    fun `banks start with the nearest bank`() {
        assertEquals("Nearest bank", searches()[3].options(login()).first().label)
    }

    @Test
    fun `fight lists every fight target`() {
        val player = login()

        assertEquals(FightTargetCatalog.fromCache().targets.size, searches()[4].options(player).size)
    }

    @Test
    fun `items list every item by name`() {
        val player = login()

        assertEquals(ItemCatalog.fromCache().items(OptionFacts()).size, searches()[5].options(player).size)
    }
}
