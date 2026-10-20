package game.idle.autopilot.travel

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.model.BankLink
import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.BankItems
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.ticksForTiles
import api.bot.webwalk.walk.crossing.BorderGateCrossing
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.flow.EntrancePassage
import game.idle.flow.LongWayCause
import game.idle.flow.Said
import game.idle.flow.TakenFor
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.flow.Way
import game.idle.movement.ObjectForms
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelChoice
import game.idle.movement.TravelWeb
import game.item.degradable.jewellery.TeleportJewellery
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
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.varp.PersistentVarp
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

    private fun login(name: String = "traveller", at: Position = here): Player = TestWorld.login(name, at)

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

        assertEquals(listOf(LongWayCause.Off(TravelChoice.COINS)) to false, trip?.longWay to trip?.plan?.legs?.any { it.type == EdgeType.CROSSING })
    }

    @Test
    fun `a trip allowed to pay takes the quick way and says nothing more`() {
        val trip = roundTrip(withCoins(20), TravelAllowance(coins = true))

        assertEquals(emptyList<LongWayCause>() to true, trip?.longWay to trip?.plan?.legs?.any { it.type == EdgeType.CROSSING })
    }

    @Test
    fun `a trip with nothing to pay with goes round and says nothing more`() {
        assertEquals(emptyList<LongWayCause>(), roundTrip(login(), TravelAllowance.NONE)?.longWay)
    }

    @Test
    fun `charges kept back that no teleport of the web uses send no trip the long way`() {
        val player = login().apply { inventory.add(Item(GLORY)) }

        assertEquals(emptyList<LongWayCause>(), roundTrip(player, TravelAllowance.NONE)?.longWay)
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

    private val stand = Position(3195, 3200)
    private val booth = Position(3195, 3201)
    private val east = Position(3215, 3200)
    private val westBank = BankDefinition("west", booth, stand, listOf(BankLink(wallWest.id, 7)))
    private val logs = 1511

    /** [tollWeb] with a bank west of the wall. */
    private val tollBankWeb: TravelWeb by lazy {
        val toll = WebWalkEdge(wallWest.id, wallEast.id, EdgeType.CROSSING, requirements = Requirements(coins = 10), action = tollGate)
        TravelWeb(WebWalkGraph(listOf(wallWest, wallEast), listOf(toll), emptyList(), emptyList(), listOf(westBank)), { false }, { wall }, Random(1))
    }

    /** A wall at x 3210 that only [teleport] crosses, landing east of it, and a bank west of it. */
    private fun teleportWeb(teleport: TeleportDefinition): TravelWeb =
        TravelWeb(WebWalkGraph(listOf(wallWest, wallEast), emptyList(), listOf(teleport), emptyList(), listOf(westBank)), { false }, { wall }, Random(1))

    private val varrockSpell = TeleportDefinition("spell_varrock", TeleportKind.SPELL, "VARROCK", option = null, wallEast.position, wallEast.id, cost = 5)
    private val gloryRub = TeleportDefinition("glory_east", TeleportKind.JEWELLERY, "AMULET_OF_GLORY", option = 1, wallEast.position, wallEast.id, cost = 5)

    /** A wall at x 3210 crossed by a toll gate at y 3200 or on foot far north, at y 3290, and a bank west of it. */
    private val farRoundWeb: TravelWeb by lazy {
        val gapWest = WebWalkNode("gapWest", Position(3209, 3290), NodeKind.WAYPOINT)
        val gapEast = WebWalkNode("gapEast", Position(3210, 3290), NodeKind.WAYPOINT)
        val edges = listOf(
            WebWalkEdge(wallWest.id, wallEast.id, EdgeType.CROSSING, requirements = Requirements(coins = 10), action = tollGate),
            WebWalkEdge(wallWest.id, gapWest.id, EdgeType.WALK),
            WebWalkEdge(gapWest.id, gapEast.id, EdgeType.WALK),
            WebWalkEdge(gapEast.id, wallEast.id, EdgeType.WALK),
        )
        val walls = WalkEstimator { from, to ->
            val crosses = (from.x < 3210) != (to.x < 3210)
            if (from.z != to.z || (crosses && (from.y < 3290 || to.y < 3290))) null else ticksForTiles(from.computeLongestDistance(to))
        }
        TravelWeb(WebWalkGraph(listOf(wallWest, wallEast, gapWest, gapEast), edges, emptyList(), emptyList(), listOf(westBank)), { false }, { walls }, Random(1))
    }

    private fun banking(player: Player, vararg items: Item): Player = player.apply { items.forEach { bank.add(it) } }

    private fun fullBag(player: Player): Player = player.apply { repeat(inventory.capacity()) { inventory.add(Item(logs)) } }

    private fun planned(player: Player, web: TravelWeb, allowance: TravelAllowance, stops: Boolean = true): TripPlanning =
        LunaTraveller(player, web, allowance, planning = direct).plan(listOf(east), stops).join()

    private val tollsAndStops = TravelAllowance(coins = true, bank = true)

    @Test
    fun `a trip with bank stops goes by the bank for a toll it cannot pay`() {
        val trip = planned(banking(login(), Item(COINS, 100)), tollBankWeb, tollsAndStops) as? PlannedTrip

        assertEquals(listOf(westBank, BankAccess.COINS, listOf(COINS), stand), listOf(trip?.stop?.bank, trip?.stop?.access, trip?.stop?.items?.map { it.id }, trip?.plan?.legs?.last()?.to))
    }

    @Test
    fun `with bank stops off, a trip only the bank makes names them in its block`() {
        assertEquals(NoTrip(TravelChoice.BANK), planned(banking(login(), Item(COINS, 100)), tollBankWeb, TravelAllowance(coins = true)))
    }

    @Test
    fun `the bank's coins count only with tolls and fares on`() {
        assertEquals(NoTrip(TravelChoice.COINS), planned(banking(login(), Item(COINS, 100)), tollBankWeb, TravelAllowance(bank = true)))
    }

    @Test
    fun `a bank holding nothing the trip may spend adds no stop`() {
        assertEquals(NoTrip(), planned(banking(login(), Item(LAW, 5)), tollBankWeb, tollsAndStops))
    }

    @Test
    fun `a trip that may not stop counts nothing in the bank`() {
        assertEquals(NoTrip(), planned(banking(login(), Item(COINS, 100)), tollBankWeb, tollsAndStops, stops = false))
    }

    @Test
    fun `a coin stack in a full bag still stops at the bank for coins`() {
        val player = login().apply {
            inventory.add(Item(COINS, 1))
            repeat(inventory.capacity() - 1) { inventory.add(Item(logs)) }
        }

        assertEquals(listOf(COINS), (planned(banking(player, Item(COINS, 100)), tollBankWeb, tollsAndStops) as? PlannedTrip)?.stop?.items?.map { it.id })
    }

    @Test
    fun `a full bag leaves the bank's coins behind and says why the trip went round`() {
        val trip = planned(banking(fullBag(login()), Item(COINS, 100)), farRoundWeb, tollsAndStops) as? PlannedTrip

        assertEquals(listOf(LongWayCause.NoRoom) to null, trip?.longWay to trip?.stop)
    }

    @Test
    fun `runes in the bank count for a spell the trip casts`() {
        val player = banking(mage().apply { inventory.clear() }, Item(AIR, 3), Item(FIRE), Item(LAW))

        val trip = planned(player, teleportWeb(varrockSpell), TravelAllowance(runes = true, bank = true)) as? PlannedTrip

        assertEquals(setOf(Item(AIR, 3), Item(FIRE), Item(LAW)), trip?.stop?.items?.toSet())
    }

    @Test
    fun `jewellery in the bank counts its charges`() {
        val trip = planned(banking(login(), Item(GLORY)), teleportWeb(gloryRub), TravelAllowance(charges = true, bank = true)) as? PlannedTrip

        assertEquals(listOf(Item(GLORY)), trip?.stop?.items)
    }

    @Test
    fun `a trip to several ends goes to the one a straight trip finds nearest`() {
        val trip = LunaTraveller(login(), tollBankWeb, planning = direct).plan(listOf(Position(3190, 3200), Position(3204, 3200))).join() as? PlannedTrip

        assertEquals(Position(3204, 3200), trip?.plan?.legs?.last()?.to)
    }

    @Test
    fun `a trip from one of its ends stays where it is`() {
        val trip = LunaTraveller(login(), tollBankWeb, planning = direct).plan(listOf(here, Position(3190, 3200))).join() as? PlannedTrip

        assertEquals(emptyList<PlanLeg>(), trip?.plan?.legs)
    }

    @Test
    fun `a trip to several ends none of which a trip reaches finds no way`() {
        assertEquals(NoTrip(), LunaTraveller(login(), tollBankWeb, planning = direct).plan(listOf(Position(3215, 3200), Position(3216, 3200))).join())
    }

    private val tollStop = BankStop(westBank, BankAccess.COINS, listOf(Item(COINS, 10)))

    private fun atBank(
        player: Player,
        allowance: TravelAllowance = tollsAndStops,
        web: TravelWeb? = tollBankWeb,
        stop: BankStop = tollStop,
        destination: Position = east,
    ): AtBank = LunaTraveller(player, web, allowance, planning = direct).atBank(stop, destination).join()

    private val gloryStop = BankStop(westBank, BankAccess.ALL, listOf(Item(GLORY)))

    private val everyChoice = TravelAllowance(coins = true, charges = true, bank = true)

    @Test
    fun `a stop with no trip on from the bank, and nothing gone from it, goes on without the bank`() {
        assertEquals(AtBank.Skip(onward = null), atBank(banking(login("stopper", stand), Item(COINS, 100)), destination = Position(3215, 3200, 1)))
    }

    @Test
    fun `a stop whose coins went from the bank but are carried now says nothing`() {
        val player = banking(login("stopper", stand), Item(COINS, 5)).apply { inventory.add(Item(COINS, 10)) }

        val turn = atBank(player) as? AtBank.Skip

        assertEquals(true to null, (turn?.onward != null) to turn?.cause)
    }

    @Test
    fun `a stop whose coins went from the bank goes round and says they are gone`() {
        val turn = atBank(banking(login("stopper", stand), Item(COINS, 5)), web = farRoundWeb) as? AtBank.Skip

        assertEquals(true to LongWayCause.TooFew(COINS), (turn?.onward != null) to turn?.cause)
    }

    @Test
    fun `a stop for jewellery carried now says nothing`() {
        val player = banking(login("stopper", stand), Item(COINS, 100)).apply { inventory.add(Item(GLORY)) }

        val turn = atBank(player, everyChoice, teleportWeb(gloryRub), gloryStop) as? AtBank.Skip

        assertEquals(true to null, (turn?.onward != null) to turn?.cause)
    }

    @Test
    fun `a stop for jewellery the trip on no longer rubs says it is gone`() {
        val turn = atBank(banking(login("stopper", stand), Item(COINS, 100)), everyChoice, teleportWeb(gloryRub), gloryStop, Position(3190, 3200)) as? AtBank.Skip

        assertEquals(LongWayCause.TooFew(GLORY), turn?.cause)
    }

    @Test
    fun `jewellery carried needs no stop with bank stops on`() {
        val player = banking(login(), Item(COINS, 100)).apply { inventory.add(Item(GLORY)) }

        val trip = planned(player, teleportWeb(gloryRub), TravelAllowance(charges = true, bank = true)) as? PlannedTrip

        assertEquals(null to true, trip?.stop to trip?.plan?.legs?.any { it.type == EdgeType.TELEPORT })
    }

    @Test
    fun `at the bank the trip on takes the bank's coins for the toll`() {
        val turn = atBank(banking(login("stopper", stand), Item(COINS, 100))) as? AtBank.Take

        assertEquals(listOf(Item(COINS, 10)) to TakenFor.Toll, turn?.items to turn?.takenFor)
    }

    @Test
    fun `at the bank the trip on crosses the toll gate`() {
        val turn = atBank(banking(login("stopper", stand), Item(COINS, 100))) as? AtBank.Take

        assertTrue(turn?.onward?.legs.orEmpty().any { it.type == EdgeType.CROSSING })
    }

    @Test
    fun `a stop without the web's data goes on without the bank`() {
        assertEquals(AtBank.Skip(onward = null), atBank(banking(login("stopper", stand), Item(COINS, 100)), web = null))
    }

    @Test
    fun `a stop whose bank stops were switched off since goes on without the bank`() {
        assertEquals(AtBank.Skip(onward = null), atBank(banking(login("stopper", stand), Item(COINS, 100)), TravelAllowance(coins = true)))
    }

    @Test
    fun `a stop whose coins are gone from the bank says so`() {
        assertEquals(AtBank.Skip(onward = null, LongWayCause.TooFew(COINS)), atBank(login("stopper", stand)))
    }

    @Test
    fun `a stop that needs nothing from the bank now goes on by the trip from it`() {
        val player = banking(login("stopper", stand), Item(COINS, 100)).apply { inventory.add(Item(COINS, 10)) }

        val turn = atBank(player) as? AtBank.Skip

        assertEquals(true to null, (turn?.onward != null) to turn?.cause)
    }

    @Test
    fun `a stop with no room left in the bag says so`() {
        assertEquals(AtBank.Skip(onward = null, LongWayCause.NoRoom), atBank(banking(fullBag(login("stopper", stand)), Item(COINS, 100))))
    }

    @Test
    fun `a worn piece the trip on rubs keeps a bag slot free at the bank`() {
        val player = banking(login("stopper", stand), Item(GLORY)).apply {
            equipment.set(Equipment.AMULET, Item(GLORY_ONE))
            repeat(inventory.capacity() - 1) { inventory.add(Item(logs)) }
        }
        val keptForReflex = Spend(charges = mapOf(TeleportJewellery.AMULET_OF_GLORY.name to 1))
        val stop = BankStop(westBank, BankAccess.ALL, listOf(Item(GLORY)))

        val turn = LunaTraveller(player, teleportWeb(gloryRub), TravelAllowance(charges = true, bank = true), keptForReflex, direct).atBank(stop, east).join()

        assertEquals(AtBank.Skip(onward = null, LongWayCause.NoRoom), turn)
    }

    private val boothId = 2213

    @Test
    fun `a booth on its tile is opened as Use-quickly opens it`() {
        TestWorld.place(boothId, booth)
        val player = login("stopper", stand)
        val clicked = clicks()

        val opened = LunaTraveller(player, web = null).openBank(booth)
        TestWorld.tick()

        assertEquals(true to listOf(ObjectSecondClickEvent::class.java), opened to clicked.map { it.javaClass })
    }

    @Test
    fun `no booth on the tile is none to open`() {
        assertFalse(LunaTraveller(login("stopper", stand), web = null).openBank(booth))
    }

    @Test
    fun `a bank the player opened holds the trip up`() {
        val player = login().apply { bank.open() }
        val traveller = LunaTraveller(player, web = null)

        assertEquals(true to true, traveller.bankOpen() to traveller.isBusy())
    }

    @Test
    fun `a bank the trip opened is its own window, which keeps no player busy`() {
        TestWorld.place(boothId, booth)
        val player = login("stopper", stand)
        val traveller = LunaTraveller(player, web = null)
        traveller.openBank(booth)
        TestWorld.tick()
        player.bank.open()

        assertFalse(traveller.isBusy())
    }

    @Test
    fun `a bank the trip clicked that did not open is no window of its own`() {
        TestWorld.place(boothId, booth)
        val player = login("stopper", stand)
        val traveller = LunaTraveller(player, web = null)
        traveller.openBank(booth)
        TestWorld.tick()

        assertFalse(traveller.isBusy())
    }

    @Test
    fun `a bank the trip closed is no longer its own`() {
        TestWorld.place(boothId, booth)
        val player = login("stopper", stand)
        val traveller = LunaTraveller(player, web = null)
        traveller.openBank(booth)
        TestWorld.tick()
        traveller.closeBank()
        player.bank.open()

        assertTrue(traveller.isBusy())
    }

    @Test
    fun `a closed bank is not open to the trip`() {
        assertFalse(LunaTraveller(login(), web = null).bankOpen())
    }

    @Test
    fun `a withdrawal brings what it asks for into the bag`() {
        val player = banking(login(), Item(COINS, 100), Item(LAW, 5))

        val withdrawn = LunaTraveller(player, web = null).withdraw(listOf(Item(LAW, 1), Item(COINS, 10)))

        assertEquals(Withdrawn(mapOf(LAW to 1, COINS to 10)) to listOf(1, 10), withdrawn to listOf(player.inventory.computeAmountForId(LAW), player.inventory.computeAmountForId(COINS)))
    }

    @Test
    fun `a withdrawal is made in items even when the player withdraws notes`() {
        val player = banking(login(), Item(logs, 3)).apply { varpManager.setAndSendValue(PersistentVarp.WITHDRAW_AS_NOTE, 1) }

        LunaTraveller(player, web = null).withdraw(listOf(Item(logs, 2)))

        assertEquals(2, player.inventory.computeAmountForId(logs))
    }

    @Test
    fun `a withdrawal stops at what the bank has none of`() {
        val player = banking(login(), Item(COINS, 100))

        assertEquals(Withdrawn(emptyMap(), LongWayCause.TooFew(LAW)), LunaTraveller(player, web = null).withdraw(listOf(Item(LAW, 1), Item(COINS, 10))))
    }

    @Test
    fun `a withdrawal that only part arrived of says there was no room`() {
        val player = banking(login(), Item(logs, 30)).apply { repeat(inventory.capacity() - 2) { inventory.add(Item(BRONZE_AXE)) } }

        assertEquals(Withdrawn(mapOf(logs to 2), LongWayCause.NoRoom), LunaTraveller(player, web = null).withdraw(listOf(Item(logs, 3))))
    }

    @Test
    fun `closing the bank closes its window`() {
        val player = login().apply { bank.open() }

        LunaTraveller(player, web = null).closeBank()

        assertFalse(player.bank.isOpen)
    }

    @Test
    fun `the bag is counted by item, with its free slots`() {
        val player = login().apply {
            inventory.add(Item(logs))
            inventory.add(Item(logs))
            inventory.add(Item(COINS, 5))
        }

        val view = bankView(player, BankItems.EMPTY)

        assertEquals(mapOf(logs to 2, COINS to 5) to player.inventory.capacity() - 3, view.inventory to view.freeSlots)
    }

    @Test
    fun `the bank's items are counted, those that stack told apart`() {
        val view = bankView(login(), BankItems(listOf(Item(COINS, 50), Item(GLORY, 2))))

        assertEquals(mapOf(COINS to 50, GLORY to 2) to setOf(COINS), view.bank to view.stackable)
    }

    @Test
    fun `worn teleport jewellery is named, as it keeps a bag slot free to rub it`() {
        val player = login().apply { equipment.set(Equipment.AMULET, Item(GLORY)) }

        assertEquals(setOf(TeleportJewellery.AMULET_OF_GLORY.name), bankView(player, BankItems.EMPTY).wornJewellery)
    }

    @Test
    fun `the bank's jewellery counts its charges, piece by piece`() {
        assertEquals(mapOf(TeleportJewellery.AMULET_OF_GLORY.name to 10), bankCharges(BankItems(listOf(Item(GLORY, 2), Item(GLORY_TWO)))))
    }

    private companion object {
        const val GLORY = 1712
        const val GLORY_TWO = 1708
        const val GLORY_ONE = 1706
        const val BRONZE_AXE = 1351
        const val COINS = 995
        const val AIR = 556
        const val FIRE = 554
        const val LAW = 563
    }
}
