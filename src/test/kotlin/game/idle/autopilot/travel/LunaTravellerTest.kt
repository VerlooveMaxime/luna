package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.CapabilitySnapshot.Companion.COINS
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.ticksForTiles
import api.bot.webwalk.walk.crossing.BorderGateCrossing
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.flow.EntrancePassage
import game.idle.flow.Said
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.flow.Way
import game.idle.movement.ObjectForms
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelChoice
import game.idle.movement.TravelWeb
import game.skill.magic.teleportSpells.TeleportSpell
import game.testworld.TestWorld
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ContinueDialogueEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.dialogue.NpcDialogue
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

    private val wallWest = WebWalkNode("west", Position(3209, 3200), NodeKind.OBSTACLE)
    private val wallEast = WebWalkNode("east", Position(3210, 3200), NodeKind.OBSTACLE)

    /** Walks anywhere on one plane except across a wall at x 3210. */
    private val wall = WalkEstimator { from, to ->
        if (from.z != to.z || (from.x < 3210) != (to.x < 3210)) null else ticksForTiles(from.computeLongestDistance(to))
    }

    private val tollGate = EdgeAction(handler = BorderGateCrossing.ID)

    /** A wall at x 3210 that only a toll gate of 10 coins crosses, from 3209 to 3210. */
    private val tollWeb: TravelWeb by lazy {
        val toll = WebWalkEdge(wallWest.id, wallEast.id, EdgeType.CROSSING, requirements = Requirements(coins = 10), action = tollGate)
        TravelWeb(WebWalkGraph(listOf(wallWest, wallEast), listOf(toll), emptyList(), emptyList()), { false }, { wall }, Random(1))
    }

    /** The legs of a trip past a wall at x 3210 that only an entrance over the object of [id] crosses, its forms the cache's. */
    private fun entranceTrip(id: Int): List<EdgeType>? {
        val entrance = WebWalkEdge(wallWest.id, wallEast.id, EdgeType.ENTRANCE, action = EdgeAction(objectId = id, objectPosition = wallEast.position))
        val web = TravelWeb(WebWalkGraph(listOf(wallWest, wallEast), listOf(entrance), emptyList(), emptyList()), { false }, { wall }, Random(1), ObjectForms.SERVER::changesForm)
        return (LunaTraveller(login(), web, planning = direct).plan(listOf(Position(3215, 3200))).join() as? PlannedTrip)?.plan?.legs?.map { it.type }
    }

    @Test
    fun `a trip goes through an entrance whose form shows the player an option`() {
        assertEquals(listOf(EdgeType.WALK, EdgeType.ENTRANCE, EdgeType.WALK), entranceTrip(6310))
    }

    @Test
    fun `a trip finds no way through an entrance the player sees no option on`() {
        assertNull(entranceTrip(6560))
    }

    @Test
    fun `an entrance reads in the words of the form the player sees`() {
        assertEquals(EntrancePassage("open", Way.THROUGH, wayNamed = false, "door"), LunaTraveller(login(), null).entrance(6310, Position(2720, 4913)))
    }

    private fun tollTrip(player: Player, allowance: TravelAllowance, reserved: Spend = Spend.NONE): List<EdgeType>? =
        (LunaTraveller(player, tollWeb, allowance, reserved, direct).plan(listOf(Position(3215, 3200))).join() as? PlannedTrip)?.plan?.legs?.map { it.type }

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
        val trip = LunaTraveller(login(), web = null).plan(listOf(Position(3260, 3200), Position(3205, 3200))).join() as? PlannedTrip
        assertEquals(listOf(EdgeType.WALK to Position(3205, 3200)), trip?.plan?.legs?.map { it.type to it.to })
    }

    @Test
    fun `without the web's data a destination on another floor has no plain walk`() {
        assertEquals(NoTrip(), LunaTraveller(login(), web = null).plan(listOf(Position(3205, 3200, 1))).join())
    }

    @Test
    fun `without the web's data the plain walk goes to the nearest destination on the player's floor`() {
        val trip = LunaTraveller(login(), web = null).plan(listOf(Position(3201, 3200, 1), Position(3220, 3200))).join() as? PlannedTrip
        assertEquals(listOf(Position(3220, 3200)), trip?.plan?.legs?.map { it.to })
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
    fun `a trip only a toll would make names the Travel choice it needs`() {
        assertEquals(NoTrip(TravelChoice.COINS), LunaTraveller(withCoins(20), tollWeb, planning = direct).plan(listOf(Position(3215, 3200))).join())
    }

    @Test
    fun `a trip nothing the player could pay with would make names no choice`() {
        assertEquals(NoTrip(), LunaTraveller(login(), tollWeb, planning = direct).plan(listOf(Position(3215, 3200))).join())
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
    fun `a click waiting for the walk up holds the trip`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        val traveller = LunaTraveller(player, web = null)
        traveller.use(door, beside, option = null, from = here)
        assertTrue(traveller.isBusy())
    }

    @Test
    fun `using an object with no option named clicks its first option`() {
        val player = login()
        TestWorld.place(door, beside)
        val clicked = clicks()
        LunaTraveller(player, web = null).use(door, beside, option = null, from = here)
        TestWorld.tick()
        assertEquals(listOf(ObjectFirstClickEvent::class.java), clicked.map { it.javaClass })
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

    private fun fleeing(player: Player) = LunaTraveller(player, web = null, fleeing = true)

    @Test
    fun `a fleeing player on the run is not held up`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertFalse(fleeing(player).isBusy())
    }

    @Test
    fun `a fleeing player's click waiting for the walk up holds them`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        val traveller = fleeing(player)

        traveller.use(door, beside, option = null, from = here)

        assertTrue(traveller.isBusy())
    }

    @Test
    fun `a fleeing walk is under way from the next tick`() {
        val player = login()

        fleeing(player).walk(Position(3205, 3200))
        TestWorld.tick()

        assertFalse(player.walking.isEmpty)
    }

    @Test
    fun `a fleeing walk breaks off what the player was doing`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        fleeing(player).walk(Position(3205, 3200))
        TestWorld.tick()

        assertNull(player.actions.first(EndlessAction::class.java))
    }

    @Test
    fun `a fleeing walk up to a door is under way from the next tick`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)

        fleeing(player).use(door, beside, option = null, from = here)
        TestWorld.tick()

        assertFalse(player.walking.isEmpty)
    }

    @Test
    fun `a fleeing click breaks off what the player was doing`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        fleeing(player).use(door, beside, option = null, from = here)
        TestWorld.tick()

        assertNull(player.actions.first(EndlessAction::class.java))
    }

    /** A wall at x 3210, crossed by a toll gate of 10 coins at y 3200 or on foot through a gap at y 3230. */
    private val roundWeb: TravelWeb by lazy {
        val west = WebWalkNode("west", Position(3209, 3200), NodeKind.OBSTACLE)
        val east = WebWalkNode("east", Position(3210, 3200), NodeKind.OBSTACLE)
        val gapWest = WebWalkNode("gapWest", Position(3209, 3230), NodeKind.WAYPOINT)
        val gapEast = WebWalkNode("gapEast", Position(3210, 3230), NodeKind.WAYPOINT)
        val edges = listOf(
            WebWalkEdge(west.id, east.id, EdgeType.CROSSING, requirements = Requirements(coins = 10), action = tollGate),
            WebWalkEdge(west.id, gapWest.id, EdgeType.WALK),
            WebWalkEdge(gapWest.id, gapEast.id, EdgeType.WALK),
            WebWalkEdge(gapEast.id, east.id, EdgeType.WALK),
        )
        val walls = WalkEstimator { from, to ->
            val crosses = (from.x < 3210) != (to.x < 3210)
            if (from.z != to.z || (crosses && (from.y < 3230 || to.y < 3230))) null else ticksForTiles(from.computeLongestDistance(to))
        }
        TravelWeb(WebWalkGraph(listOf(west, east, gapWest, gapEast), edges, emptyList(), emptyList()), { false }, { walls }, Random(1))
    }

    private fun roundTrip(player: Player, allowance: TravelAllowance): PlannedTrip? =
        LunaTraveller(player, roundWeb, allowance, planning = direct).plan(listOf(Position(3215, 3200))).join() as? PlannedTrip

    @Test
    fun `a trip the flow's rules send the long way round says which choice would have been quicker`() {
        val trip = roundTrip(withCoins(20), TravelAllowance.NONE)

        assertEquals(setOf(TravelChoice.COINS) to false, trip?.longWay to trip?.plan?.legs?.any { it.type == EdgeType.CROSSING })
    }

    @Test
    fun `a trip allowed to pay takes the quick way and says nothing more`() {
        val trip = roundTrip(withCoins(20), TravelAllowance(coins = true))

        assertEquals(emptySet<TravelChoice>() to true, trip?.longWay to trip?.plan?.legs?.any { it.type == EdgeType.CROSSING })
    }

    @Test
    fun `a trip with nothing to pay with goes round and says nothing more`() {
        assertEquals(emptySet<TravelChoice>(), roundTrip(login(), TravelAllowance.NONE)?.longWay)
    }

    @Test
    fun `charges kept back that no teleport of the web uses send no trip the long way`() {
        val player = login().apply { inventory.add(Item(GLORY)) }

        assertEquals(emptySet<TravelChoice>(), roundTrip(player, TravelAllowance.NONE)?.longWay)
    }

    private fun spoken(player: Player): NpcDialogue = NpcDialogue(926, Expression.DEFAULT, "You must pay a toll of 10 gold coins to pass.").also { player.overlays.open(it) }

    @Test
    fun `a dialogue's line keeps no player busy, the trip clicks on from it`() {
        val player = login()
        spoken(player)
        assertFalse(LunaTraveller(player, web = null).isBusy())
    }

    @Test
    fun `a dialogue's line is talk to click on from, not a question`() {
        val player = login()
        spoken(player)
        val traveller = LunaTraveller(player, web = null)
        assertEquals(true to false, traveller.talking() to traveller.choosing())
    }

    @Test
    fun `a question is no line to click on from`() {
        val player = login()
        asked(player)
        assertFalse(LunaTraveller(player, web = null).talking())
    }

    @Test
    fun `with no dialogue open there is nothing to click on from`() {
        assertFalse(LunaTraveller(login(), web = null).talking())
    }

    private fun continued(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ContinueDialogueEvent::class.java) { clicked += it.widgetId }
        return clicked
    }

    @Test
    fun `clicking on from a line continues the dialogue as the client does`() {
        val player = login()
        val line = spoken(player)
        val clicked = continued()
        LunaTraveller(player, web = null).talkOn()
        assertEquals(listOf(line.id), clicked)
    }

    @Test
    fun `with no line open clicking on does nothing`() {
        val clicked = continued()
        LunaTraveller(login(), web = null).talkOn()
        assertEquals(emptyList<Int>(), clicked)
    }

    private fun webTeleport(kind: TeleportKind, key: String?) = TeleportDefinition("teleport", kind, key, option = null, Position(3212, 3423), "node", cost = 5)

    /** A plain player with Magic 25 and the runes for a Varrock teleport. */
    private fun mage(): Player = login().apply {
        rights = PlayerRights.PLAYER
        skill(Skill.MAGIC).staticLevel = 25
        skill(Skill.MAGIC).level = 25
        inventory.add(Item(AIR, 3))
        inventory.add(Item(FIRE))
        inventory.add(Item(LAW))
    }

    @Test
    fun `a teleport of the web is cast as the reflexes cast it, saying what it takes`() {
        val started = LunaTraveller(mage(), web = null).teleport(webTeleport(TeleportKind.SPELL, "VARROCK"), {}) {}

        assertEquals(TeleportStarted.Cast(TeleportDestination.Spell(TeleportSpell.VARROCK), mapOf(AIR to 3, FIRE to 1, LAW to 1)), started)
    }

    @Test
    fun `a teleport no reflex offers is not started`() {
        assertNull(LunaTraveller(mage(), web = null).teleport(webTeleport(TeleportKind.HOME, key = null), {}) {})
    }

    @Test
    fun `a teleport the player cannot do now is not started`() {
        val player = mage().apply { lock() }
        assertNull(LunaTraveller(player, web = null).teleport(webTeleport(TeleportKind.SPELL, "VARROCK"), {}) {})
    }

    @Test
    fun `what the trip says goes to the step's speech at once`() {
        val heard = mutableListOf<Said>()
        val traveller = LunaTraveller(login(), web = null, speech = heard::add)

        traveller.say(Said.PaidToll(10))

        assertEquals(listOf(Said.PaidToll(10)), heard)
    }

    @Test
    fun `a question's options are read in order`() {
        val player = login()
        asked(player)
        assertEquals(listOf("Climb up", "Climb down", "Nevermind"), LunaTraveller(player, web = null).options())
    }

    @Test
    fun `a line offers no options`() {
        val player = login()
        spoken(player)
        assertEquals(emptyList<String>(), LunaTraveller(player, web = null).options())
    }

    private val sailor = 376

    @Test
    fun `the crew is talked to when one stands within reach of the dock`() {
        val player = login()
        TestWorld.spawnNpc(sailor, beside)
        val talked = mutableListOf<Int>()
        TestWorld.listen(NpcFirstClickEvent::class.java) { talked += it.targetNpc.id }

        val found = LunaTraveller(player, web = null).talkTo(setOf(sailor), Position(3205, 3200))
        TestWorld.tick()

        assertEquals(true to listOf(sailor), found to talked)
    }

    @Test
    fun `no crew within reach of the dock is none to talk to`() {
        val player = login()
        TestWorld.spawnNpc(sailor, Position(3230, 3200))

        assertFalse(LunaTraveller(player, web = null).talkTo(setOf(sailor), Position(3205, 3200)))
    }

    private companion object {
        const val GLORY = 1712
        const val AIR = 556
        const val FIRE = 554
        const val LAW = 563
    }
}
