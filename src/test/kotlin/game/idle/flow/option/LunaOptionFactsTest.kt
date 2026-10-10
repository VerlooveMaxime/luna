package game.idle.flow.option

import game.idle.IdleState
import game.idle.idleState
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LunaOptionFactsTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    /** A player with Woodcutting 28, boosted to 31. */
    private fun boostedWoodcutter(): Player {
        val player = TestWorld.login("facts", Position(3200, 3200))
        val woodcutting = player.skills.getSkill(Skill.WOODCUTTING)
        woodcutting.staticLevel = 28
        woodcutting.level = 31
        return player
    }

    @Test
    fun `options judge unboosted levels by default`() {
        assertEquals(28, LunaOptionFacts.of(boostedWoodcutter()).level(Skill.WOODCUTTING))
    }

    @Test
    fun `a player who counts boosts has options judged on boosted levels`() {
        val player = boostedWoodcutter()
        player.idleState = IdleState(countBoostedLevels = true)

        assertEquals(31, LunaOptionFacts.of(player).level(Skill.WOODCUTTING))
    }

    @Test
    fun `the facts hold every skill`() {
        assertEquals(Skill.NAMES.size, LunaOptionFacts.of(boostedWoodcutter()).levels.size)
    }

    @Test
    fun `the facts count what the bank holds`() {
        val player = boostedWoodcutter()
        player.bank.add(Item(1521, 312))
        player.bank.add(Item(995, 40))

        assertEquals(mapOf(1521 to 312, 995 to 40), LunaOptionFacts.of(player).bank)
    }

    @Test
    fun `the facts hold what the bag holds`() {
        val player = boostedWoodcutter()
        player.inventory.add(Item(1351))
        player.inventory.add(Item(1511))
        player.inventory.add(Item(1511))

        assertEquals(setOf(1351, 1511), LunaOptionFacts.of(player).bag)
    }

    @Test
    fun `the facts hold what the player wears`() {
        val player = boostedWoodcutter()
        player.equipment.set(Equipment.WEAPON, Item(1351))

        assertEquals(setOf(1351), LunaOptionFacts.of(player).worn)
    }

    @Test
    fun `the cache names items, npcs and objects`() {
        TestWorld.context

        assertEquals(
            listOf("Willow logs", "Cow", "Willow"),
            listOf(LunaGameNames.item(1519), LunaGameNames.npc(81), LunaGameNames.obj(1308)),
        )
    }
}
