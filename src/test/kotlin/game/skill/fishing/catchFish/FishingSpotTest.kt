package game.skill.fishing.catchFish

import game.testworld.TestWorld
import io.luna.game.model.def.NpcDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The spot table against the cache: each spot's options name the tools the table gives them. */
class FishingSpotTest {

    /** Tools name their fish from the item definitions, and the spots are cache npcs. */
    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    /** The cache's word for the option each tool fishes with. */
    private val optionWord = mapOf(
        Tool.SMALL_NET to "Net",
        Tool.BIG_NET to "Net",
        Tool.MONKFISH_NET to "Net",
        Tool.FISHING_ROD to "Bait",
        Tool.FLY_FISHING_ROD to "Lure",
        Tool.LOBSTER_POT to "Cage",
        Tool.HARPOON to "Harpoon",
        Tool.SHARK_HARPOON to "Harpoon",
    )

    private fun spots(): List<Pair<Int, FishingSpot>> = FishingSpot.entries.flatMap { spot -> spot.ids.map { it to spot } }

    /** The cache's options of npc [id]; an option it does not have reads "null". */
    private fun actions(id: Int): List<String> = NpcDefinition.ALL.retrieve(id).actions

    @Test
    fun `every spot in the table is a fishing spot in the cache`() {
        assertEquals(emptyList<Int>(), spots().map { it.first }.filter { NpcDefinition.ALL.retrieve(it).name != "Fishing spot" })
    }

    @Test
    fun `each spot's first option is the one its first tool fishes with`() {
        assertEquals(spots().map { optionWord[it.second.firstClick] }, spots().map { actions(it.first)[0] })
    }

    @Test
    fun `each spot's second option, Luna's second click, is the one its second tool fishes with`() {
        assertEquals(spots().map { it.second.secondClick?.let(optionWord::get) ?: "null" }, spots().map { actions(it.first)[2] })
    }

    @Test
    fun `no spot is in two rows`() {
        assertEquals(spots().size, spots().map { it.first }.toSet().size)
    }

    @Test
    fun `every tool but the karambwan vessel has spots`() {
        val withoutSpots = Tool.entries.filter { FishingSpot.firstClickIds(it).isEmpty() && FishingSpot.secondClickIds(it).isEmpty() }

        assertEquals(listOf(Tool.KARAMBWAN_VESSEL), withoutSpots)
    }

    @Test
    fun `Catherby's net and harpoon spot nets big net fish and harpoons sharks`() {
        assertEquals(listOf(Tool.BIG_NET, Tool.SHARK_HARPOON), FishingSpot.entries.single { 322 in it.ids }.let { listOf(it.firstClick, it.secondClick) })
    }

    @Test
    fun `the Lumbridge river spot is lured on its first option and baited on its second`() {
        assertEquals(listOf(true, true), listOf(329 in FishingSpot.firstClickIds(Tool.FLY_FISHING_ROD), 329 in FishingSpot.secondClickIds(Tool.FISHING_ROD)))
    }
}
