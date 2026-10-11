package game.idle.flow

import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PreconditionsTest {

    private val bronzeAxe = 1351
    private val runeAxe = 1359
    private val net = 303
    private val bait = 313
    private val carried = FakeCarried()
    private val axe = ToolNeed("axe", mapOf(bronzeAxe to 1, runeAxe to 41), Skill.WOODCUTTING, worn = true)
    private val smallNet = ToolNeed(word = null, mapOf(net to 1))
    private val fishingBait = ToolNeed(word = null, mapOf(bait to 1), countable = false)
    private val oak = LevelNeed(Skill.WOODCUTTING, 15)

    private fun check(vararg ways: StepNeeds): Reason? = Preconditions.check(ways.toList(), carried)

    @Test
    fun `a step that needs nothing is never held`() {
        assertNull(check())
    }

    @Test
    fun `a way that needs nothing is clear`() {
        assertNull(check(StepNeeds()))
    }

    @Test
    fun `a tool carried is enough`() {
        carried.bag += bronzeAxe

        assertNull(check(StepNeeds(tools = listOf(axe))))
    }

    @Test
    fun `a tool missing is named`() {
        assertEquals(Reason.NeedsTool(axe), check(StepNeeds(tools = listOf(axe))))
    }

    @Test
    fun `a tool worn counts when Luna counts it worn`() {
        carried.worn += bronzeAxe

        assertNull(check(StepNeeds(tools = listOf(axe))))
    }

    @Test
    fun `a tool worn does not count when Luna only looks in the bag`() {
        carried.worn += net

        assertEquals(Reason.NeedsTool(smallNet), check(StepNeeds(tools = listOf(smallNet))))
    }

    @Test
    fun `a tool above the player's level does not count`() {
        carried.bag += runeAxe

        assertEquals(Reason.NeedsTool(axe), check(StepNeeds(tools = listOf(axe))))
    }

    @Test
    fun `a tool at the player's level counts`() {
        carried.bag += runeAxe
        carried.levels[Skill.WOODCUTTING] = 41

        assertNull(check(StepNeeds(tools = listOf(axe))))
    }

    @Test
    fun `a tool without a skill counts at any level`() {
        carried.bag += net

        assertNull(check(StepNeeds(tools = listOf(smallNet))))
    }

    @Test
    fun `the tools are asked for in the way's order, bait after the tool`() {
        assertEquals(Reason.NeedsTool(smallNet), check(StepNeeds(tools = listOf(smallNet, fishingBait))))
    }

    @Test
    fun `bait is asked for once the tool is there`() {
        carried.bag += net

        assertEquals(Reason.NeedsTool(fishingBait), check(StepNeeds(tools = listOf(smallNet, fishingBait))))
    }

    @Test
    fun `a level too low is named`() {
        carried.levels[Skill.WOODCUTTING] = 14

        assertEquals(Reason.NeedsLevel(Skill.WOODCUTTING, 15), check(StepNeeds(level = oak)))
    }

    @Test
    fun `the level met is enough`() {
        carried.levels[Skill.WOODCUTTING] = 15

        assertNull(check(StepNeeds(level = oak)))
    }

    @Test
    fun `the tool is asked for before the level`() {
        assertEquals(Reason.NeedsTool(axe), check(StepNeeds(tools = listOf(axe), level = oak)))
    }

    @Test
    fun `one clear way among several is enough`() {
        carried.bag += net

        assertNull(check(StepNeeds(tools = listOf(axe)), StepNeeds(tools = listOf(smallNet))))
    }

    @Test
    fun `with no way clear the first way's first lack is named`() {
        carried.levels[Skill.WOODCUTTING] = 14

        assertEquals(Reason.NeedsTool(smallNet), check(StepNeeds(tools = listOf(smallNet), level = oak), StepNeeds(tools = listOf(axe))))
    }
}
