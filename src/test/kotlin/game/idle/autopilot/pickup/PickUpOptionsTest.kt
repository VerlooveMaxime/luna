package game.idle.autopilot.pickup

import engine.spawn.ItemSpawn
import game.idle.flow.FlowContext
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.option.FakeNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import game.idle.location.Tile
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PickUpOptionsTest {

    private val cow = 81
    private val bones = 526
    private val cowhide = 1739
    private val egg = 1944
    private val bucket = 1925
    private val coins = 995
    private val pen = Tile(3253, 3270)
    private val drops = DropLookup { if (it == cow) setOf(bones, cowhide) else emptySet() }
    private val spawns = SpawnCatalog(listOf(ItemSpawn(egg, 1, Position(3255, 3270), 100), ItemSpawn(bucket, 1, Position(3200, 3200), 100)))
    private val names = FakeNames(
        items = mapOf(bones to "Bones", cowhide to "Cowhide", egg to "Egg", bucket to "Bucket", coins to "Coins"),
        npcs = mapOf(cow to "Cow"),
    )
    private val items = ItemCatalog(mapOf(bones to "Bones", egg to "Egg", coins to "Coins"))
    private val source = PickUpOptions(drops, spawns, items, names)

    private fun row(id: Int, name: String, note: String, group: Int = 0) = StepOption(id.toString(), name, OptionIcon.Item(id), note, group = group)

    @Test
    fun `after a fight step its npc's drops come first`() {
        val rows = source.options(OptionContext(before = FlowContext(fought = setOf(cow))))

        assertEquals(listOf(row(bones, "Bones", "Cow drop", -2), row(cowhide, "Cowhide", "Cow drop", -2)), rows.take(2))
    }

    @Test
    fun `items that spawn within the step's distance of the walk step's tile follow`() {
        val context = OptionContext(settings = StepSettings("pickup", mapOf("within" to "5")), before = FlowContext(WorkSpot.At(pen)))

        assertEquals(row(egg, "Egg", "spawns here", -1), source.options(context).first())
    }

    @Test
    fun `without a walk step the spawns are those around the player`() {
        val context = OptionContext(here = Tile(3201, 3201))

        assertEquals(row(bucket, "Bucket", "spawns here", -1), source.options(context).first())
    }

    @Test
    fun `without a walk step or a tile there are no spawns, only any item`() {
        val rows = source.options(OptionContext())

        assertEquals(listOf("any item"), rows.map { it.note }.distinct())
    }

    @Test
    fun `any item follows, leaving out what is already listed`() {
        val rows = source.options(OptionContext(before = FlowContext(WorkSpot.At(pen), fought = setOf(cow))))

        assertEquals(listOf(row(coins, "Coins", "any item")), rows.drop(3))
    }
}
