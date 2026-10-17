package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.CapabilitySnapshot.Companion.COINS
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.ticksForTiles
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelWeb
import game.testworld.TestWorld
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.OptionDialogue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.concurrent.Executor

class LunaTravellerTest {

    private val here = Position(3200, 3200)
    private val beside = Position(3201, 3200)
    private val ladder = 1748
    private val door = 1530
    private val direct = Executor { it.run() }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(name: String = "traveller"): Player = TestWorld.login(name, here)

    /** A wall at x 3210 that only a toll gate of 10 coins crosses, from 3209 to 3210. */
    private val tollWeb: TravelWeb by lazy {
        val west = WebWalkNode("west", Position(3209, 3200), NodeKind.OBSTACLE)
        val east = WebWalkNode("east", Position(3210, 3200), NodeKind.OBSTACLE)
        val toll = WebWalkEdge(west.id, east.id, EdgeType.CROSSING, requirements = Requirements(coins = 10))
        val walls = WalkEstimator { from, to ->
            if (from.z != to.z || (from.x < 3210) != (to.x < 3210)) null else ticksForTiles(from.computeLongestDistance(to))
        }
        TravelWeb(WebWalkGraph(listOf(west, east), listOf(toll), emptyList(), emptyList()), { false }, { walls }, Random(1))
    }

    private fun tollTrip(player: Player, allowance: TravelAllowance, reserved: Spend = Spend.NONE): List<EdgeType>? =
        LunaTraveller(player, tollWeb, allowance, reserved, direct).plan(listOf(Position(3215, 3200))).join()?.legs?.map { it.type }

    private fun withCoins(amount: Int): Player = login().apply { inventory.add(Item(COINS, amount)) }

    private fun clicks(): MutableList<ObjectClickEvent> {
        val clicked = mutableListOf<ObjectClickEvent>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it }
        TestWorld.listen(ObjectSecondClickEvent::class.java) { clicked += it }
        TestWorld.listen(ObjectThirdClickEvent::class.java) { clicked += it }
        return clicked
    }

    private fun buttons(): MutableList<Int> {
        val pressed = mutableListOf<Int>()
        TestWorld.listen(ButtonClickEvent::class.java) { pressed += it.id }
        return pressed
    }

    private fun asked(player: Player): OptionDialogue =
        OptionDialogue("Climb up", "Climb down", "Nevermind").also { player.overlays.open(it) }

    @Test
    fun `the traveller stands where the player does`() {
        assertEquals(here, LunaTraveller(login(), web = null).position())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)
        assertTrue(LunaTraveller(player, web = null).isBusy())
    }

    @Test
    fun `the up-or-down question keeps no player busy, the trip answers it`() {
        val player = login()
        asked(player)
        assertFalse(LunaTraveller(player, web = null).isBusy())
    }

    @Test
    fun `without the web's data a trip is the plain walk to the nearest destination`() {
        val trip = LunaTraveller(login(), web = null).plan(listOf(Position(3260, 3200), Position(3205, 3200))).join()
        assertEquals(listOf(EdgeType.WALK to Position(3205, 3200)), trip?.legs?.map { it.type to it.to })
    }

    @Test
    fun `a toll is not paid unless the flow allows coins`() {
        assertNull(tollTrip(withCoins(20), TravelAllowance.NONE))
    }

    @Test
    fun `a toll the flow allows is paid`() {
        assertEquals(listOf(EdgeType.WALK, EdgeType.CROSSING, EdgeType.WALK), tollTrip(withCoins(20), TravelAllowance(coins = true)))
    }

    @Test
    fun `coins held back for a reflex are not spent on a toll`() {
        assertNull(tollTrip(withCoins(20), TravelAllowance(coins = true), Spend(items = mapOf(COINS to 15))))
    }

    @Test
    fun `a trip is planned on the planning threads`() {
        val queued = mutableListOf<Runnable>()
        val trip = LunaTraveller(login(), tollWeb, planning = { queued += it }).plan(listOf(Position(3205, 3200)))
        assertEquals(false to 1, trip.isDone to queued.size)
    }

    @Test
    fun `a walk heads for its tile`() {
        val player = login()
        LunaTraveller(player, web = null).walk(Position(3205, 3200))
        assertEquals(Position(3205, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `an object on its tile is standing`() {
        TestWorld.place(door, beside)
        assertTrue(LunaTraveller(login(), web = null).standing(door, beside))
    }

    @Test
    fun `an object gone from its tile is not standing`() {
        assertFalse(LunaTraveller(login(), web = null).standing(door, beside))
    }

    @Test
    fun `another object on the tile is not the one standing`() {
        TestWorld.place(ladder, beside)
        assertFalse(LunaTraveller(login(), web = null).standing(door, beside))
    }

    @Test
    fun `an object only another player sees is not standing`() {
        val player = login()
        TestWorld.placeFor(login("someone"), door, beside)
        assertFalse(LunaTraveller(player, web = null).standing(door, beside))
    }

    @Test
    fun `using an object that is gone clicks nothing`() {
        assertFalse(LunaTraveller(login(), web = null).use(door, beside, option = null))
    }

    @Test
    fun `using an option the object lacks clicks nothing`() {
        TestWorld.place(door, beside)
        assertFalse(LunaTraveller(login(), web = null).use(door, beside, "Climb-up"))
    }

    @Test
    fun `using an object with no option named clicks its first option`() {
        val player = login()
        TestWorld.place(door, beside)
        val clicked = clicks()
        LunaTraveller(player, web = null).use(door, beside, option = null)
        TestWorld.tick()
        assertEquals(listOf(ObjectFirstClickEvent::class.java), clicked.map { it.javaClass })
    }

    @Test
    fun `an option is clicked by its place among the object's options`() {
        val player = login()
        TestWorld.place(ladder, beside)
        val clicked = clicks()
        LunaTraveller(player, web = null).use(ladder, beside, "Climb-up")
        TestWorld.tick()
        assertEquals(listOf(ObjectSecondClickEvent::class.java), clicked.map { it.javaClass })
    }

    @Test
    fun `a third option is clicked as the third`() {
        val player = login()
        TestWorld.place(ladder, beside)
        val clicked = clicks()
        LunaTraveller(player, web = null).use(ladder, beside, "Climb-down")
        TestWorld.tick()
        assertEquals(listOf(ObjectThirdClickEvent::class.java), clicked.map { it.javaClass })
    }

    @Test
    fun `an option dialogue is a question to choose from`() {
        val player = login()
        asked(player)
        assertTrue(LunaTraveller(player, web = null).choosing())
    }

    @Test
    fun `choosing presses the option's button, after the dialogue's title`() {
        val player = login()
        val dialogue = asked(player)
        val pressed = buttons()
        LunaTraveller(player, web = null).choose(2)
        assertEquals(listOf(dialogue.id + 3), pressed)
    }

    @Test
    fun `choosing with no question open presses nothing`() {
        val pressed = buttons()
        LunaTraveller(login(), web = null).choose(1)
        assertEquals(emptyList<Int>(), pressed)
    }

    @Test
    fun `interrupting breaks off what the player does`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()
        LunaTraveller(player, web = null).interrupt()
        TestWorld.tick()
        assertFalse(LunaClicks.isActing(player))
    }
}
