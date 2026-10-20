package game.idle.autopilot

import api.bot.webwalk.plan.Spend
import engine.controllers.WildernessController
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.idle.IdleState
import game.idle.flow.ReflexSettings
import game.idle.flow.StepSettings
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.idleState
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.teleportSpells.TeleportSpell
import game.testworld.TestWorld
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaTeleportsTest {

    private val here = Position(3200, 3200)
    private val edgeville = TeleportDestination.Jewellery(TeleportJewellery.AMULET_OF_GLORY, 0)
    private val burthorpe = TeleportDestination.Jewellery(TeleportJewellery.GAMES_NECKLACE, 0)
    private val varrock = TeleportDestination.Spell(TeleportSpell.VARROCK)
    private val lumbridge = TeleportDestination.Spell(TeleportSpell.LUMBRIDGE)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("rubber", here)

    private fun wearing(id: Int): Player = login().apply { equipment.set(Equipment.AMULET, Item(id)) }

    /** Stands in for `changeEquipment.kts`: the equipment tab's first option takes an item off, Wear puts one on. */
    private fun equipmentTab() {
        TestWorld.listen(WidgetItemFirstClickEvent::class.java) { if (it.widgetId == 1688) it.plr.equipment.unequip(it.index) }
        TestWorld.listen(EquipItemEvent::class.java) { it.plr.equipment.equip(it.index) }
    }

    private fun fill(player: Player) {
        repeat(player.inventory.computeRemainingSize()) { player.inventory.add(Item(LOGS)) }
    }

    @Test
    fun `a worn piece can be rubbed while the bag has room to take it off into`() {
        assertTrue(LunaTeleports.canTeleport(wearing(GLORY), edgeville))
    }

    @Test
    fun `a worn piece cannot be rubbed with the bag full`() {
        val player = wearing(GLORY)
        fill(player)

        assertFalse(LunaTeleports.canTeleport(player, edgeville))
    }

    @Test
    fun `jewellery neither carried nor worn cannot be rubbed`() {
        assertFalse(LunaTeleports.canTeleport(login(), edgeville))
    }

    @Test
    fun `a worn piece is taken off and rubbed, which the start says`() {
        equipmentTab()

        assertEquals(TeleportStarted.Rubbed(edgeville, tookOff = true), LunaTeleports.teleport(wearing(GLORY), edgeville))
    }

    @Test
    fun `a worn piece is worn again once the player lands, a charge less`() {
        equipmentTab()
        val player = wearing(GLORY)
        LunaTeleports.teleport(player, edgeville)

        TestWorld.tick(8)

        assertEquals(listOf<Any?>(edgeville.destination.centre, GLORY_THREE, false), listOf(player.position, player.equipment[Equipment.AMULET]?.id, player.inventory.contains(GLORY_THREE)))
    }

    @Test
    fun `a worn piece taken off to rub it keeps the player acting until it is worn again`() {
        equipmentTab()
        val player = wearing(GLORY)
        LunaTeleports.teleport(player, edgeville)
        val waiting = player.wearingAgain

        TestWorld.tick(8)

        assertEquals(true to false, waiting to player.wearingAgain)
    }

    @Test
    fun `a worn piece whose last charge was used is worn again uncharged`() {
        equipmentTab()
        val player = wearing(GLORY_ONE)
        LunaTeleports.teleport(player, edgeville)

        TestWorld.tick(8)

        assertEquals(GLORY_EMPTY, player.equipment[Equipment.AMULET]?.id)
    }

    @Test
    fun `a necklace that crumbles leaves nothing to wear again`() {
        equipmentTab()
        val player = wearing(GAMES_NECKLACE_ONE)
        LunaTeleports.teleport(player, burthorpe)

        TestWorld.tick(10)

        assertEquals(listOf<Any?>(burthorpe.destination.centre, null), listOf(player.position, player.equipment[Equipment.AMULET]))
    }

    @Test
    fun `a worn piece is rubbed from the bag slot it was taken off into`() {
        equipmentTab()
        val player = wearing(GLORY)
        player.inventory.add(Item(LOGS))
        LunaTeleports.teleport(player, edgeville)

        TestWorld.tick(8)

        assertEquals(LOGS to GLORY_THREE, player.inventory[0]?.id to player.equipment[Equipment.AMULET]?.id)
    }

    @Test
    fun `a worn piece the equipment tab keeps on is not rubbed`() {
        assertNull(LunaTeleports.teleport(wearing(GLORY), edgeville))
    }

    @Test
    fun `a piece taken off for a teleport Luna turns down is worn again at once`() {
        equipmentTab()
        val player = wearing(GLORY)
        player.controllers.register(WildernessController(player))
        player.wildernessLevel = 31
        val started = LunaTeleports.teleport(player, edgeville)

        TestWorld.tick()

        assertEquals(null to GLORY, started to player.equipment[Equipment.AMULET]?.id)
    }

    @Test
    fun `a piece is not worn again over what the player put on meanwhile`() {
        equipmentTab()
        val player = wearing(GLORY)
        LunaTeleports.teleport(player, edgeville)
        player.equipment.set(Equipment.AMULET, Item(STRENGTH_AMULET))

        TestWorld.tick(8)

        assertEquals(STRENGTH_AMULET to true, player.equipment[Equipment.AMULET]?.id to player.inventory.contains(GLORY_THREE))
    }

    @Test
    fun `what took the piece's place in the bag is not worn`() {
        equipmentTab()
        val player = wearing(GLORY)
        LunaTeleports.teleport(player, edgeville)
        val slot = (0 until player.inventory.capacity()).first { player.inventory[it]?.id == GLORY }
        player.inventory.set(slot, Item(LOGS))

        TestWorld.tick(8)

        assertNull(player.equipment[Equipment.AMULET])
    }

    @Test
    fun `what took a necklace's place in the bag is not worn`() {
        equipmentTab()
        val player = wearing(GAMES_NECKLACE)
        LunaTeleports.teleport(player, burthorpe)
        val slot = (0 until player.inventory.capacity()).first { player.inventory[it]?.id == GAMES_NECKLACE }
        player.inventory.set(slot, Item(LOGS))

        TestWorld.tick(10)

        assertNull(player.equipment[Equipment.AMULET])
    }

    @Test
    fun `a player gone before landing has nothing worn again`() {
        equipmentTab()
        val player = wearing(GLORY)
        LunaTeleports.teleport(player, edgeville)
        TestWorld.world.players.remove(player)

        TestWorld.tick(8)

        assertNull(player.equipment[Equipment.AMULET])
    }

    private val starts = mutableListOf<TeleportStarted>()

    private val notWorn = mutableListOf<TeleportDestination.Jewellery>()

    private fun inTheDeepWilderness(player: Player): Player = player.apply {
        controllers.register(WildernessController(this))
        wildernessLevel = 31
    }

    @Test
    fun `a cast says it starts before Luna's own line`() {
        val player = login()

        LunaTeleports.teleport(player, varrock, starting = { player.sendMessage("started") })

        assertEquals(listOf("started", "You teleport to Varrock."), TestWorld.chatbox(player))
    }

    @Test
    fun `a cast Luna turns down says nothing of its start`() {
        LunaTeleports.teleport(inTheDeepWilderness(login()), varrock, starting = { starts += it })

        assertEquals(emptyList<TeleportStarted>(), starts)
    }

    @Test
    fun `a rub says it starts before Luna's own lines`() {
        val player = login().apply { inventory.add(Item(GLORY)) }

        LunaTeleports.teleport(player, edgeville, starting = { player.sendMessage("started") })

        assertEquals(listOf("started", TeleportJewellery.AMULET_OF_GLORY.rub), TestWorld.chatbox(player).take(2))
    }

    @Test
    fun `a rub Luna turns down says nothing of its start`() {
        val player = inTheDeepWilderness(login().apply { inventory.add(Item(GLORY)) })

        LunaTeleports.teleport(player, edgeville, starting = { starts += it })

        assertEquals(emptyList<TeleportStarted>(), starts)
    }

    @Test
    fun `a piece worn again is not told`() {
        equipmentTab()
        LunaTeleports.teleport(wearing(GLORY), edgeville, notWornAgain = { notWorn += it })

        TestWorld.tick(8)

        assertEquals(emptyList<TeleportDestination.Jewellery>(), notWorn)
    }

    @Test
    fun `a piece left off for what the player put on meanwhile is told`() {
        equipmentTab()
        val player = wearing(GLORY)
        LunaTeleports.teleport(player, edgeville, notWornAgain = { notWorn += it })
        player.equipment.set(Equipment.AMULET, Item(STRENGTH_AMULET))

        TestWorld.tick(8)

        assertEquals(listOf(edgeville), notWorn)
    }

    @Test
    fun `a piece Luna would not put back on is told`() {
        TestWorld.listen(WidgetItemFirstClickEvent::class.java) { if (it.widgetId == 1688) it.plr.equipment.unequip(it.index) }
        LunaTeleports.teleport(wearing(GLORY), edgeville, notWornAgain = { notWorn += it })

        TestWorld.tick(8)

        assertEquals(listOf(edgeville), notWorn)
    }

    @Test
    fun `a necklace that crumbles is not told`() {
        equipmentTab()
        LunaTeleports.teleport(wearing(GAMES_NECKLACE_ONE), burthorpe, notWornAgain = { notWorn += it })

        TestWorld.tick(10)

        assertEquals(emptyList<TeleportDestination.Jewellery>(), notWorn)
    }

    @Test
    fun `charges count every piece in the bag`() {
        val player = login()
        player.inventory.add(Item(GLORY))
        player.inventory.add(Item(GLORY_ONE))

        assertEquals(mapOf("AMULET_OF_GLORY" to 5), LunaTeleports.charges(player))
    }

    @Test
    fun `charges count the worn piece while the bag has room for it`() {
        val player = wearing(GLORY)
        player.inventory.add(Item(GLORY_ONE))

        assertEquals(mapOf("AMULET_OF_GLORY" to 5), LunaTeleports.charges(player))
    }

    @Test
    fun `charges leave out the worn piece with the bag full`() {
        val player = wearing(GLORY)
        fill(player)

        assertEquals(emptyMap<String, Int>(), LunaTeleports.charges(player))
    }

    @Test
    fun `with no jewellery there are no charges`() {
        assertEquals(emptyMap<String, Int>(), LunaTeleports.charges(login()))
    }

    /** A plain player (development mode would let a developer cast anything) with Magic 25 and the runes for a Varrock teleport. */
    private fun mage(): Player = login().apply {
        rights = PlayerRights.PLAYER
        skill(Skill.MAGIC).staticLevel = 25
        skill(Skill.MAGIC).level = 25
        inventory.add(Item(AIR, 3))
        inventory.add(Item(FIRE))
        inventory.add(Item(LAW))
    }

    @Test
    fun `a spell cast for free says it used nothing up`() {
        assertEquals(TeleportStarted.Cast(varrock, spent = emptyMap()), LunaTeleports.teleport(login(), varrock))
    }

    @Test
    fun `a spell cast with runes says what it used up, in the spell's order`() {
        assertEquals(TeleportStarted.Cast(varrock, mapOf(AIR to 3, FIRE to 1, LAW to 1)), LunaTeleports.teleport(mage(), varrock))
    }

    @Test
    fun `a spell the player can cast keeps back what Luna would take for it`() {
        assertEquals(Spend(items = mapOf(AIR to 3, FIRE to 1, LAW to 1)), LunaTeleports.spend(mage(), varrock))
    }

    @Test
    fun `a spell the player cannot cast yet keeps back what it lists that no worn staff gives`() {
        val player = login()
        player.equipment.set(Equipment.WEAPON, Item(STAFF_OF_AIR))

        assertEquals(Spend(items = mapOf(FIRE to 1, LAW to 1)), LunaTeleports.spend(player, varrock))
    }

    @Test
    fun `jewellery keeps back a charge`() {
        assertEquals(Spend(charges = mapOf("AMULET_OF_GLORY" to 1)), LunaTeleports.spend(login(), edgeville))
    }

    @Test
    fun `a trip keeps back the next cast of every teleport reflex of the flow, summed`() {
        val player = login()
        player.idleState = IdleState(
            steps = listOf(StepSettings("chop", reflexes = listOf(1, 2, 3))),
            reflexes = listOf(
                ReflexSettings(1, mapOf("do" to "teleport", "destination" to "varrock")),
                ReflexSettings(2, mapOf("do" to "teleport", "destination" to "lumbridge")),
                ReflexSettings(3, mapOf("do" to "teleport", "destination" to "amulet_of_glory:edgeville")),
            ),
        )

        assertEquals(
            Spend(items = mapOf(AIR to 6, FIRE to 1, LAW to 2, EARTH to 1), charges = mapOf("AMULET_OF_GLORY" to 1)),
            LunaTeleports.reserve(player),
        )
    }

    @Test
    fun `a flow without teleport reflexes keeps nothing back`() {
        assertEquals(Spend.NONE, LunaTeleports.reserve(login()))
    }

    private companion object {
        const val GLORY = 1712
        const val GLORY_THREE = 1710
        const val GLORY_ONE = 1706
        const val GLORY_EMPTY = 1704
        const val GAMES_NECKLACE = 3853
        const val GAMES_NECKLACE_ONE = 3867
        const val STRENGTH_AMULET = 1725
        const val STAFF_OF_AIR = 1381
        const val LOGS = 1511
        const val AIR = 556
        const val FIRE = 554
        const val LAW = 563
        const val EARTH = 557
    }
}
