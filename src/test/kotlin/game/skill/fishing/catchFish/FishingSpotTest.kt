package game.skill.fishing.catchFish

import game.testworld.TestWorld
import io.luna.game.model.def.NpcDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The spot table against the cache: each option the table fishes is the option the cache shows for the spot. */
class FishingSpotTest {

    /** Tools name their fish from the item definitions, and the spots are cache npcs. */
    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    /** The cache's word for the option each tool fishes with. */
    private val optionWord = mapOf(
        Tool.SMALL_NET to "Net",
        Tool.KARAMBWANJI_NET to "Net",
        Tool.BIG_NET to "Net",
        Tool.MONKFISH_NET to "Net",
        Tool.FISHING_ROD to "Bait",
        Tool.PIKE_FISHING_ROD to "Bait",
        Tool.SLIMY_EEL_FISHING_ROD to "Bait",
        Tool.CAVE_EEL_FISHING_ROD to "Bait",
        Tool.LAVA_EEL_FISHING_ROD to "Bait",
        Tool.FLY_FISHING_ROD to "Lure",
        Tool.LOBSTER_POT to "Cage",
        Tool.HARPOON to "Harpoon",
        Tool.SHARK_HARPOON to "Harpoon",
        Tool.KARAMBWAN_VESSEL to "Fish",
    )

    private fun spots(): List<Pair<Int, FishingSpot>> = FishingSpot.entries.flatMap { spot -> spot.npcs.map { it to spot } }

    /** The cache's options of npc [id]; an option it does not have reads "null". */
    private fun actions(id: Int): List<String> = NpcDefinition.ALL.retrieve(id).actions

    @Test
    fun `every spot npc in the table is a fishing spot in the cache`() {
        assertEquals(emptyList<Int>(), spots().map { it.first }.filter { NpcDefinition.ALL.retrieve(it).name != "Fishing spot" })
    }

    @Test
    fun `each spot's first option, where the table fishes it, is the one its first tool fishes with`() {
        val fished = spots().filter { it.second.firstOption != null }

        assertEquals(fished.map { optionWord[it.second.firstOption] }, fished.map { actions(it.first)[0] })
    }

    @Test
    fun `each spot's second option, Luna's second click, is the one its second tool fishes with`() {
        assertEquals(spots().map { it.second.secondOption?.let(optionWord::get) ?: "null" }, spots().map { actions(it.first)[2] })
    }

    @Test
    fun `no spot npc is in two rows`() {
        assertEquals(spots().size, spots().map { it.first }.toSet().size)
    }

    @Test
    fun `Tutorial Island's spot is netted for shrimp and anchovies`() {
        assertEquals(listOf(Tool.SMALL_NET, null), FishingSpot.entries.single { 952 in it.npcs }.let { listOf(it.firstOption, it.secondOption) })
    }
}
