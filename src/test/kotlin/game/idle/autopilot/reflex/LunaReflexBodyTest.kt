package game.idle.autopilot.reflex

import game.idle.autopilot.EndlessAction
import game.idle.flow.Health
import game.testworld.TestWorld
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaReflexBodyTest {

    private val here = Position(3200, 3200)
    private val chicken = 41
    private val bread = 2309
    private val trout = 333
    private val logs = 1511
    private val deadTree = 1286

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("reflexes", here)

    private fun attackedBy(player: Player, at: Position): Npc = TestWorld.spawnNpc(chicken, at).also { it.combat.target = player }

    private fun eats(): MutableList<Pair<Int, Int>> {
        val eaten = mutableListOf<Pair<Int, Int>>()
        TestWorld.listen(ItemFirstClickEvent::class.java) { eaten += it.id to it.index }
        return eaten
    }

    @Test
    fun `health is the player's hitpoints out of their full hitpoints`() {
        val player = login()
        player.health = 6

        assertEquals(Health(6, 10), LunaReflexBody(player).health())
    }

    @Test
    fun `with no food picked the first food in the bag is found`() {
        val player = login()
        player.inventory.set(0, Item(logs))
        player.inventory.set(2, Item(bread))

        assertEquals(2, LunaReflexBody(player).foodSlot(emptySet()))
    }

    @Test
    fun `with foods picked only those are found, first in bag order`() {
        val player = login()
        player.inventory.set(1, Item(bread))
        player.inventory.set(3, Item(trout))

        assertEquals(3, LunaReflexBody(player).foodSlot(setOf(trout)))
    }

    @Test
    fun `without food there is no food slot`() {
        val player = login()
        player.inventory.add(Item(logs))

        assertNull(LunaReflexBody(player).foodSlot(emptySet()))
    }

    @Test
    fun `eating clicks the food like the client`() {
        val player = login()
        player.inventory.set(4, Item(bread))
        val eaten = eats()

        LunaReflexBody(player).eat(4)

        assertEquals(listOf(bread to 4), eaten)
    }

    @Test
    fun `an empty slot is not eaten`() {
        val eaten = eats()

        LunaReflexBody(login()).eat(4)

        assertEquals(emptyList<Pair<Int, Int>>(), eaten)
    }

    @Test
    fun `a locked player does not eat`() {
        val player = login()
        player.inventory.set(4, Item(bread))
        player.lock()
        val eaten = eats()

        LunaReflexBody(player).eat(4)

        assertEquals(emptyList<Pair<Int, Int>>(), eaten)
    }

    @Test
    fun `an npc fighting the player with an open way attacks them`() {
        val player = login()
        attackedBy(player, Position(3203, 3200))

        assertTrue(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `an npc fighting the player but fenced in does not attack them`() {
        val player = login()
        val npc = attackedBy(player, Position(3205, 3200))
        Direction.ALL_EXCEPT_NONE.forEach { TestWorld.place(deadTree, npc.position.translate(1, it)) }

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `an npc fighting someone else does not attack the player`() {
        val player = login()
        attackedBy(TestWorld.login("other", Position(3204, 3200)), Position(3203, 3200))

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `a dead npc does not attack`() {
        val player = login()
        attackedBy(player, Position(3203, 3200)).health = 0

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `with nothing attacking there is nowhere to run`() {
        assertFalse(LunaReflexBody(login()).flee())
    }

    @Test
    fun `fleeing runs from the attacker`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertFalse(player.walking.isEmpty)
    }

    @Test
    fun `fleeing says there was somewhere to run`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))

        assertTrue(LunaReflexBody(player).flee())
    }

    @Test
    fun `fleeing stops what the player was doing`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        player.submitAction(EndlessAction(player))

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertNull(player.actions.first(EndlessAction::class.java))
    }

    @Test
    fun `a player with hitpoints left is alive`() {
        assertFalse(LunaReflexBody(login()).dead())
    }

    @Test
    fun `a player with no hitpoints left is dead`() {
        val player = login()
        player.health = 0

        assertTrue(LunaReflexBody(player).dead())
    }
}
