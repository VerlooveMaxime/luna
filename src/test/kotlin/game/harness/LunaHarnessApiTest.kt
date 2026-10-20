package game.harness

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.CapabilitySnapshot.Companion.COINS
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.ticksForTiles
import game.idle.IdleState
import game.idle.flow.FakeBankStep
import game.idle.flow.FakeStep
import game.idle.flow.FakeStepType
import game.idle.flow.FlowCheck
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexResolver
import game.idle.flow.ReflexSettings
import game.idle.flow.RunProgress
import game.idle.flow.StepNeeds
import game.idle.flow.StepTypes
import game.idle.flow.StopSettings
import game.idle.font.ClientFont
import game.idle.idleState
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelWeb
import game.testworld.TestWorld
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.event.Event
import game.idle.ui.MapPickEvent
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import game.idle.ui.SearchClosedEvent
import game.idle.ui.SearchNameEvent
import game.idle.ui.SearchPageEvent
import game.idle.ui.SearchPickEvent
import game.idle.ui.SearchPrompts
import io.luna.game.event.impl.ArrangeItemEvent
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.CloseInterfaceEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NumberInputEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.Random

class LunaHarnessApiTest {

    private val spawn = Position(3200, 3200)
    private val boothTile = Position(3201, 3200)
    private val booth = 2213
    private val inertBooth = 2214
    private val normalTree = 1276
    private val man = 1
    private val bronzeAxe = 1351
    private val wall = 1902
    private val range = 3039
    private val door = 1530
    private val logs = 1511

    private class Endless(player: Player) : Action<Player>(player, ActionType.WEAK) {
        override fun run(): Boolean = false
    }

    /** A chop whose slot says its word, so a flow view carries the slot's lines; it takes a goal (S12). */
    private val chop = FakeStepType("chop", detailsOf = { settings, _ -> listOfNotNull(settings[FakeStepType.WORD]) }, goal = true)

    /** A light that takes oak logs in, and a bank that withdraws them, for the withdrawals left out (S12). */
    private val light = FakeStepType("light", resolved = { FakeStep("light", needs = listOf(StepNeeds(inputs = listOf(1521)))) })
    private val bank = FakeStepType("bank", resolved = { FakeBankStep(setOf(1521, 1511)) })
    private val flows = FlowCheck(FlowResolver(StepTypes(listOf(chop, FakeStepType("drop"), light, bank))), ReflexResolver(mapOf(333 to setOf(333)), emptyMap()), maxSteps = 2, maxReflexes = 2)

    private fun api() = LunaHarnessApi(
        TestWorld.world,
        LunaGameThread(TestWorld.context.game, Duration.ofSeconds(1)),
        HeadlessPlayers(TestWorld.context, HarnessConfig()) { CompletableFuture.completedFuture(null) },
        flows,
    )

    private fun apiOn(date: String) = LunaHarnessApi(
        TestWorld.world,
        LunaGameThread(TestWorld.context.game, Duration.ofSeconds(1)),
        HeadlessPlayers(TestWorld.context, HarnessConfig()) { CompletableFuture.completedFuture(null) },
        flows,
        clock = Clock.fixed(Instant.parse("${date}T12:00:00Z"), ZoneOffset.UTC),
    )

    /** Trips planned on [web]; with none, as before its data is generated, a plain walk on the player's floor, none off it. */
    private fun withWeb(web: TravelWeb? = null) = LunaHarnessApi(
        TestWorld.world,
        LunaGameThread(TestWorld.context.game, Duration.ofSeconds(1)),
        HeadlessPlayers(TestWorld.context, HarnessConfig()) { CompletableFuture.completedFuture(null) },
        flows,
        web = { web },
    )

    private fun tile(x: Int, y: Int, z: Int? = null): TileView = withWeb().tile("agent_a", x, y, z)

    /** A wall at x 3210 that only a toll gate of 10 coins crosses, from 3209 to 3210. */
    private val tollWeb: TravelWeb by lazy {
        val west = WebWalkNode("west", Position(3209, 3200), NodeKind.OBSTACLE)
        val east = WebWalkNode("east", Position(3210, 3200), NodeKind.OBSTACLE)
        val wall = WalkEstimator { from, to ->
            if (from.z != to.z || (from.x < 3210) != (to.x < 3210)) null else ticksForTiles(from.computeLongestDistance(to))
        }
        val toll = WebWalkEdge(west.id, east.id, EdgeType.CROSSING, requirements = Requirements(coins = 10))
        TravelWeb(WebWalkGraph(listOf(west, east), listOf(toll), emptyList(), emptyList()), { false }, { wall }, Random(1))
    }

    /** The agent carrying the toll's 10 coins, with a flow whose trips may spend as [travel] says. */
    private fun payingAgent(travel: TravelAllowance): Player = agent().apply {
        inventory.add(Item(COINS, 10))
        idleState = idleState.copy(travel = travel)
    }

    private fun agent(position: Position = spawn): Player = TestWorld.login("agent_a", position)

    /** The agent with a search open, its second prompt (serial 1), so a packet with serial 0 would not match it. */
    private fun searching(): Player = agent().also {
        SearchPrompts.openName(it, "Name for this flow:", "", 20) { _, _ -> }
        SearchPrompts.open(it, "Which tree?", listOf(StepOption("oak", "Oak", OptionIcon.Item(1521))), ClientFont(IntArray(256) { 5 })) { _, _ -> }
    }

    /** The agent with a name prompt open, its second prompt (serial 1). */
    private fun naming(): Player = agent().also {
        SearchPrompts.openName(it, "Name for this flow:", "", 20) { _, _ -> }
        SearchPrompts.openName(it, "Name for this flow:", "", 20) { _, _ -> }
    }

    private fun status(call: () -> Any): Int = assertThrows<HarnessException> { call() }.status

    private fun <E : Event> record(type: Class<E>, describe: (E) -> String): MutableList<String> {
        val events = mutableListOf<String>()
        TestWorld.listen(type) { events += describe(it) }
        return events
    }

    private fun clickBooth() = PlayerAction.ClickObject(boothTile.x, boothTile.y, booth, option = 1)

    @AfterEach
    fun resetWorld() = TestWorld.reset()


    @Test
    fun `the world view counts players, bots and harness players`() {
        agent()
        TestWorld.loginWithRealClient("human", spawn)
        TestWorld.bot("botty", spawn)

        val view = api().world()

        assertEquals(listOf(3, 1, 1), listOf(view.playersOnline, view.botsOnline, view.harnessPlayersOnline))
    }

    @Test
    fun `the world view carries the current tick`() {
        assertEquals(TestWorld.world.currentTick, api().world().tick)
    }


    @Test
    fun `players are listed by name, without bots`() {
        TestWorld.login("zed", spawn)
        TestWorld.login("adam", spawn)
        TestWorld.bot("botty", spawn)

        assertEquals(listOf("adam", "zed"), api().players(includeBots = false).map { it.name })
    }

    @Test
    fun `bots are listed when asked for`() {
        TestWorld.login("adam", spawn)
        TestWorld.bot("botty", spawn)

        assertEquals(listOf("adam", "botty"), api().players(includeBots = true).map { it.name })
    }

    @Test
    fun `a player summary says who is a harness player and where they stand`() {
        val player = agent()

        val summary = api().players(includeBots = false).single()

        assertEquals(PlayerSummary("agent_a", player.index, bot = false, harness = true, PositionView(3200, 3200, 0)), summary)
    }


    @Test
    fun `login puts a headless player in the world and shows them`() {
        val view = api().login("agent_one")

        assertEquals("agent_one" to true, view.name to view.harness)
    }

    @Test
    fun `logout requests a forced logout of a headless player`() {
        val api = api()
        api.login("agent_one")

        api.logout("agent_one")

        assertTrue(TestWorld.world.logoutService.hasRequest("agent_one"))
    }

    @Test
    fun `logout answers that the save finishes later`() {
        val api = api()
        api.login("agent_one")

        val view = api.logout("agent_one")

        assertEquals(ActionView("agent_one", "logout", "forced logout requested; the save finishes asynchronously"), view)
    }


    @Test
    fun `a player who is not online is a 404`() {
        assertEquals(404, status { api().player("nobody") })
    }

    @Test
    fun `the name is matched ignoring case and surrounding spaces`() {
        agent()

        assertEquals("agent_a", api().player(" Agent_A ").name)
    }

    @Test
    fun `the player view says who the player is and where they stand`() {
        val player = agent()

        val view = api().player("agent_a")

        assertEquals(
            listOf("agent_a", player.index, player.rights.name, false, true, PositionView(3200, 3200, 0)),
            listOf(view.name, view.index, view.rights, view.bot, view.harness, view.position),
        )
    }

    @Test
    fun `the player view reads run energy, running and combat level`() {
        agent()

        val view = api().player("agent_a")

        assertEquals(listOf(100.0, false, 3), listOf(view.runEnergy, view.running, view.combatLevel))
    }

    @Test
    fun `the player view reads the special attack bar's energy`() {
        agent().combat.specialBar.setEnergy(40)

        assertEquals(40, api().player("agent_a").specialEnergy)
    }

    @Test
    fun `the player view says when the player is walking`() {
        agent().walking.addStep(Direction.EAST)

        assertTrue(api().player("agent_a").walking)
    }

    @Test
    fun `the player view lists every skill`() {
        agent()

        val skills = api().player("agent_a").skills

        assertEquals(SkillView(Skill.WOODCUTTING, "Woodcutting", 1, 1, 0.0), skills[Skill.WOODCUTTING])
    }

    @Test
    fun `the player view lists inventory items with their menu options`() {
        agent().inventory.add(Item(bronzeAxe))

        val options = listOf(OptionView(2, "Wield"), OptionView(5, "Drop"))
        assertEquals(listOf(ItemView(0, bronzeAxe, "Bronze axe", 1, options)), api().player("agent_a").inventory)
    }

    @Test
    fun `the player view lists equipment without menu options`() {
        agent().equipment.set(Equipment.WEAPON, Item(bronzeAxe))

        val equipment = api().player("agent_a").equipment

        assertEquals(listOf(ItemView(Equipment.WEAPON, bronzeAxe, "Bronze axe", 1, emptyList())), equipment)
    }

    @Test
    fun `the player view counts the bank's used slots`() {
        agent().bank.add(Item(logs, 3))

        assertEquals(1, api().player("agent_a").bankItems)
    }

    @Test
    fun `the player view names the running actions`() {
        val player = agent()
        player.submitAction(Endless(player))

        assertEquals(listOf("Endless"), api().player("agent_a").actions)
    }

    @Test
    fun `the player view names the open overlays by type`() {
        agent().overlays.open(StandardInterface(5292))

        assertEquals(mapOf("WIDGET_STANDARD" to "StandardInterface"), api().player("agent_a").overlays)
    }

    @Test
    fun `the player view shows saved attributes by their plain key`() {
        agent().idleState = IdleState(stepIndex = 2)

        val attributes = api().player("agent_a").attributes

        assertEquals(2, attributes.getValue("idle_state").asJsonObject["step_index"].asInt)
    }


    @Test
    fun `a radius below 1 is refused`() {
        agent()

        assertEquals(400, status { api().nearby("agent_a", radius = 0, includeInert = false) })
    }

    @Test
    fun `a radius above 32 is refused`() {
        agent()

        assertEquals(400, status { api().nearby("agent_a", radius = 33, includeInert = false) })
    }

    @Test
    fun `nearby is centred on the player`() {
        agent()

        val view = api().nearby("agent_a", radius = 5, includeInert = false)

        assertEquals(listOf(PositionView(3200, 3200, 0), 5), listOf(view.center, view.radius))
    }

    @Test
    fun `objects with menu options are listed nearest first`() {
        agent()
        TestWorld.place(normalTree, Position(3196, 3200))
        TestWorld.place(booth, boothTile)

        val objects = api().nearby("agent_a", radius = 5, includeInert = false).objects

        assertEquals(listOf(booth, normalTree), objects.map { it.id })
    }

    @Test
    fun `an object is shown with its name, place, distance, type and options`() {
        agent()
        TestWorld.place(booth, boothTile)

        val objects = api().nearby("agent_a", radius = 5, includeInert = false).objects

        val options = listOf(OptionView(1, "Use"), OptionView(2, "Use-quickly"))
        assertEquals(listOf(ObjectView(booth, "Bank booth", PositionView(3201, 3200, 0), 1, "DEFAULT", options)), objects)
    }

    @Test
    fun `objects without options are left out`() {
        agent()
        TestWorld.place(inertBooth, boothTile)

        assertEquals(emptyList<ObjectView>(), api().nearby("agent_a", radius = 5, includeInert = false).objects)
    }

    @Test
    fun `objects without options are listed when asked for`() {
        agent()
        TestWorld.place(inertBooth, boothTile)

        val objects = api().nearby("agent_a", radius = 5, includeInert = true).objects

        assertEquals(listOf(inertBooth), objects.map { it.id })
    }

    @Test
    fun `an object only another player sees is left out`() {
        agent()
        TestWorld.placeFor(TestWorld.login("other", spawn), booth, boothTile)

        assertEquals(emptyList<ObjectView>(), api().nearby("agent_a", radius = 5, includeInert = true).objects)
    }

    @Test
    fun `npcs in view are listed with their options`() {
        agent()
        val npc = TestWorld.spawnNpc(man, Position(3202, 3200))

        val npcs = api().nearby("agent_a", radius = 5, includeInert = false).npcs

        val options = listOf(OptionView(1, "Talk-to"), OptionView(2, "Attack"), OptionView(3, "Pickpocket"))
        assertEquals(listOf(NpcView(npc.index, man, "Man", PositionView(3202, 3200, 0), 2, 2, options)), npcs)
    }

    @Test
    fun `ground items the player sees are listed with their options`() {
        agent()
        TestWorld.dropItem(logs, 1, Position(3201, 3201))

        val items = api().nearby("agent_a", radius = 5, includeInert = false).groundItems

        val options = listOf(OptionView(3, "Take"), OptionView(4, "Light"))
        assertEquals(listOf(GroundItemView(logs, "Logs", 1, PositionView(3201, 3201, 0), 1, options)), items)
    }

    @Test
    fun `a ground item only another player sees is left out`() {
        agent()
        TestWorld.dropItem(logs, 1, Position(3201, 3201), viewer = TestWorld.login("other", spawn))

        assertEquals(emptyList<GroundItemView>(), api().nearby("agent_a", radius = 5, includeInert = false).groundItems)
    }

    @Test
    fun `other players nearby are listed, not the player themself`() {
        agent()
        val other = TestWorld.login("agent_b", Position(3203, 3200))

        val players = api().nearby("agent_a", radius = 5, includeInert = false).players

        assertEquals(listOf(NearbyPlayerView("agent_b", other.index, false, PositionView(3203, 3200, 0), 3)), players)
    }


    @Test
    fun `open ground a walk gets to is a tile a player can be put on`() {
        agent()

        assertEquals(TileView(PositionView(3202, 3200, 0), ok = true, standable = true, objects = emptyList(), reachable = true, problems = emptyList()), tile(3202, 3200))
    }

    @Test
    fun `the tile is read on the player's floor when none is given`() {
        agent(Position(3200, 3200, 1))

        assertEquals(PositionView(3202, 3200, 1), tile(3202, 3200).tile)
    }

    @Test
    fun `a wall on the tile fails it, though a player can stand there`() {
        agent()
        TestWorld.place(door, Position(3202, 3200), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

        val view = tile(3202, 3200)

        assertEquals(listOf(true, false, listOf("a wall stands on it: Door (straight wall)")), listOf(view.standable, view.ok, view.problems))
    }

    @Test
    fun `a wall without a name is told by its kind alone`() {
        agent()
        TestWorld.place(wall, Position(3202, 3200), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

        assertEquals(listOf("a wall stands on it (straight wall)"), tile(3202, 3200).problems)
    }

    @Test
    fun `an object covering the tile past its south-west tile is listed and blocks it`() {
        agent()
        TestWorld.place(range, Position(3201, 3200), direction = ObjectDirection.NORTH)

        val view = tile(3202, 3200)

        assertEquals(listOf(listOf(range), false), listOf(view.objects.map { it.id }, view.standable))
    }

    @Test
    fun `the objects covering a tile are listed nearest south-west tile first`() {
        agent()
        TestWorld.place(range, Position(3201, 3200), direction = ObjectDirection.NORTH)
        TestWorld.place(wall, Position(3202, 3200), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

        assertEquals(listOf(wall, range), tile(3202, 3200).objects.map { it.id })
    }

    @Test
    fun `an object on the tile is shown as nearby shows it, its distance counted from the player`() {
        agent()
        TestWorld.place(booth, Position(3203, 3200))

        val options = listOf(OptionView(1, "Use"), OptionView(2, "Use-quickly"))
        assertEquals(listOf(ObjectView(booth, "Bank booth", PositionView(3203, 3200, 0), 3, "DEFAULT", options)), tile(3203, 3200).objects)
    }

    @Test
    fun `an object on a tile far out of the player's view is still on it`() {
        agent()
        TestWorld.place(booth, Position(3250, 3200))

        assertEquals(listOf(booth), tile(3250, 3200).objects.map { it.id })
    }

    @Test
    fun `an object beside the tile is not on it`() {
        agent()
        TestWorld.place(booth, boothTile)

        assertEquals(emptyList<ObjectView>(), tile(3202, 3200).objects)
    }

    @Test
    fun `an object only another player sees is not on the tile`() {
        agent()
        TestWorld.placeFor(TestWorld.login("other", spawn), booth, Position(3202, 3200))

        assertEquals(emptyList<ObjectView>(), tile(3202, 3200).objects)
    }

    @Test
    fun `a tile no trip gets to fails it, saying where the trip would start`() {
        agent()

        val view = tile(3202, 3200, z = 1)

        assertEquals(listOf(false, false, listOf("no trip from 3200, 3200 gets there with this flow's Travel settings")), listOf(view.reachable, view.ok, view.problems))
    }

    @Test
    fun `a tile past a toll is reachable when the flow's Travel tab lets its trips pay`() {
        payingAgent(TravelAllowance(coins = true))

        assertTrue(withWeb(tollWeb).tile("agent_a", 3215, 3200, z = null).reachable)
    }

    @Test
    fun `a tile past a toll is out of reach when the flow's Travel tab keeps its coins`() {
        payingAgent(TravelAllowance.NONE)

        assertEquals(false, withWeb(tollWeb).tile("agent_a", 3215, 3200, z = null).reachable)
    }

    @Test
    fun `the tile check of a player not online is a 404`() {
        assertEquals(404, status { tile(3202, 3200) })
    }

    @Test
    fun `a move onto open ground is queued like any command`() {
        agent()

        val view = api().act("agent_a", PlayerAction.Command("::move 3202 3200"))

        assertEquals(ActionView("agent_a", "command", "opcode 56 queued for the next tick"), view)
    }

    @Test
    fun `a move onto a tile the player would stand wrong on is a 409 saying why`() {
        agent()
        TestWorld.place(booth, Position(3203, 3200))

        val thrown = assertThrows<HarnessException> { api().act("agent_a", PlayerAction.Command("::move 3203 3200")) }

        val reason = "not moving agent_a onto 3203, 3200, 0: a player cannot stand on it; an object stands on it: Bank booth"
        assertEquals(listOf(409, reason), listOf(thrown.status, thrown.message))
    }

    @Test
    fun `a move needs no trip to its tile`() {
        agent()

        val view = withWeb().act("agent_a", PlayerAction.Command("::move 3202 3200 1"))

        assertEquals(ActionView("agent_a", "command", "opcode 56 queued for the next tick"), view)
    }

    @Test
    fun `a headless player's messages are read from its log`() {
        agent().sendMessage("Welcome to IdleRS.")

        val messages = api().messages("agent_a", since = 0, type = "chatbox").messages

        assertEquals(listOf("Welcome to IdleRS."), messages.map { it.fields["message"] })
    }

    @Test
    fun `a bot's messages are not recorded`() {
        TestWorld.bot("botty", spawn)

        assertEquals(501, status { api().messages("botty", since = 0, type = null) })
    }


    @Test
    fun `a bot is not driven through the harness`() {
        TestWorld.bot("botty", spawn)

        assertEquals(409, status { api().act("botty", PlayerAction.CloseInterface) })
    }

    @Test
    fun `clicking a missing object is a 404`() {
        agent()

        assertEquals(404, status { api().act("agent_a", clickBooth()) })
    }

    @Test
    fun `clicking an object that is not on its tile is a 404, whatever else is there`() {
        agent()
        TestWorld.place(inertBooth, boothTile)

        assertEquals(404, status { api().act("agent_a", clickBooth()) })
    }

    @Test
    fun `clicking an object only another player sees is a 404`() {
        agent()
        TestWorld.placeFor(TestWorld.login("other", spawn), booth, boothTile)

        assertEquals(404, status { api().act("agent_a", clickBooth()) })
    }

    @Test
    fun `clicking an object walks the player to it`() {
        agent()
        TestWorld.place(booth, boothTile)

        val view = api().act("agent_a", clickBooth())

        assertEquals(ActionView("agent_a", "click object", "walking to Bank booth; option 1 on arrival"), view)
    }

    @Test
    fun `the object click is sent once the player is next to the object`() {
        agent()
        TestWorld.place(booth, boothTile)
        val clicked = record(ObjectFirstClickEvent::class.java) { "${it.gameObject.id}" }

        api().act("agent_a", clickBooth())
        TestWorld.tick(times = 2)

        assertEquals(listOf("$booth"), clicked)
    }

    @Test
    fun `an object click whose walk was cancelled is never sent`() {
        val player = agent()
        TestWorld.place(booth, boothTile)
        val clicked = record(ObjectFirstClickEvent::class.java) { "${it.gameObject.id}" }

        api().act("agent_a", clickBooth())
        player.navigator.cancel()
        TestWorld.tick(times = 2)

        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun `the object click of a player who left before arriving is dropped`() {
        val player = agent()
        TestWorld.place(booth, boothTile)
        val clicked = record(ObjectFirstClickEvent::class.java) { "${it.gameObject.id}" }

        api().act("agent_a", clickBooth())
        TestWorld.world.players.remove(player)
        // What the next ticks would do with anything the client had queued, had the player stayed in the world.
        player.client.handleDecodedMessages()
        player.actions.process()

        assertEquals(emptyList<String>(), clicked)
    }

    @Test
    fun `clicking a missing npc is a 404`() {
        agent()

        assertEquals(404, status { api().act("agent_a", PlayerAction.ClickNpc(index = 100, option = 1)) })
    }

    @Test
    fun `clicking an npc out of view is a 404`() {
        agent()
        val npc = TestWorld.spawnNpc(man, Position(3220, 3200))

        assertEquals(404, status { api().act("agent_a", PlayerAction.ClickNpc(npc.index, option = 1)) })
    }

    @Test
    fun `clicking an npc walks the player to it`() {
        agent()
        val npc = TestWorld.spawnNpc(man, Position(3201, 3200))

        val view = api().act("agent_a", PlayerAction.ClickNpc(npc.index, option = 1))

        assertEquals(ActionView("agent_a", "click npc", "walking to Man; option 1 on arrival"), view)
    }

    @Test
    fun `the npc click is sent once the player is next to the npc`() {
        agent()
        val npc = TestWorld.spawnNpc(man, Position(3201, 3200))
        val clicked = record(NpcFirstClickEvent::class.java) { "${it.targetNpc.id}" }

        api().act("agent_a", PlayerAction.ClickNpc(npc.index, option = 1))
        TestWorld.tick(times = 2)

        assertEquals(listOf("$man"), clicked)
    }

    @Test
    fun `an inventory slot below 0 is refused`() {
        agent()

        assertEquals(400, status { api().act("agent_a", PlayerAction.ClickItem(slot = -1, id = null, option = 1)) })
    }

    @Test
    fun `an inventory slot past the last is refused`() {
        agent()

        assertEquals(400, status { api().act("agent_a", PlayerAction.ClickItem(slot = 28, id = null, option = 1)) })
    }

    @Test
    fun `clicking an empty inventory slot is a 404`() {
        agent()

        assertEquals(404, status { api().act("agent_a", PlayerAction.ClickItem(slot = 0, id = null, option = 1)) })
    }

    @Test
    fun `clicking a slot that holds another item than named is a 409`() {
        agent().inventory.add(Item(bronzeAxe))

        assertEquals(409, status { api().act("agent_a", PlayerAction.ClickItem(slot = 0, id = logs, option = 1)) })
    }

    @Test
    fun `clicking the item named in its slot queues the item click`() {
        agent().inventory.add(Item(bronzeAxe))

        val view = api().act("agent_a", PlayerAction.ClickItem(slot = 0, id = bronzeAxe, option = 1))

        assertEquals(ActionView("agent_a", "click item", "opcode 203 queued for the next tick"), view)
    }

    @Test
    fun `clicking an item without naming it clicks whatever is in the slot on the next tick`() {
        agent().inventory.add(Item(bronzeAxe))
        val clicked = record(ItemFirstClickEvent::class.java) { "${it.id}" }

        api().act("agent_a", PlayerAction.ClickItem(slot = 0, id = null, option = 1))
        TestWorld.tick()

        assertEquals(listOf("$bronzeAxe"), clicked)
    }

    @Test
    fun `clicking a missing ground item is a 404`() {
        agent()

        assertEquals(404, status { api().act("agent_a", PlayerAction.ClickGroundItem(3201, 3201, logs, option = 3)) })
    }

    @Test
    fun `clicking a ground item that is not on its tile is a 404, whatever else is there`() {
        agent()
        TestWorld.dropItem(bronzeAxe, 1, Position(3201, 3201))

        assertEquals(404, status { api().act("agent_a", PlayerAction.ClickGroundItem(3201, 3201, logs, option = 3)) })
    }

    @Test
    fun `clicking a ground item only another player sees is a 404`() {
        agent()
        TestWorld.dropItem(logs, 1, Position(3201, 3201), viewer = TestWorld.login("other", spawn))

        assertEquals(404, status { api().act("agent_a", PlayerAction.ClickGroundItem(3201, 3201, logs, option = 3)) })
    }

    @Test
    fun `clicking a ground item walks the player to it`() {
        agent()
        TestWorld.dropItem(logs, 1, Position(3201, 3201))

        val view = api().act("agent_a", PlayerAction.ClickGroundItem(3201, 3201, logs, option = 3))

        assertEquals(ActionView("agent_a", "click ground item", "walking to Logs; option 3 on arrival"), view)
    }

    @Test
    fun `walking heads for the tile on the player's floor`() {
        val player = agent()

        api().act("agent_a", PlayerAction.Walk(3205, 3200))

        assertEquals(Position(3205, 3200, 0), player.navigator.currentTarget)
    }

    @Test
    fun `walking answers where the player is heading`() {
        agent()

        val view = api().act("agent_a", PlayerAction.Walk(3205, 3200))

        assertEquals(ActionView("agent_a", "walk", "walking to 3205, 3200"), view)
    }

    @Test
    fun `a command is queued like the client's`() {
        agent()

        val view = api().act("agent_a", PlayerAction.Command("::tutorial build_chain"))

        assertEquals(ActionView("agent_a", "command", "opcode 56 queued for the next tick"), view)
    }

    @Test
    fun `a command is handled on the next tick as if typed`() {
        agent()
        val commands = record(CommandEvent::class.java) { "${it.name} ${it.args.joinToString()}" }

        api().act("agent_a", PlayerAction.Command("::tutorial build_chain"))
        TestWorld.tick()

        assertEquals(listOf("tutorial build_chain"), commands)
    }

    @Test
    fun `chat is queued like the client's`() {
        agent()

        val view = api().act("agent_a", PlayerAction.Chat("hello"))

        assertEquals(ActionView("agent_a", "chat", "opcode 49 queued for the next tick"), view)
    }

    @Test
    fun `a button press is queued like the client's`() {
        agent()

        val view = api().act("agent_a", PlayerAction.Button(5387))

        assertEquals(ActionView("agent_a", "button", "opcode 79 queued for the next tick"), view)
    }

    @Test
    fun `a button is pressed on the next tick`() {
        agent()
        val buttons = record(ButtonClickEvent::class.java) { "${it.id}" }

        api().act("agent_a", PlayerAction.Button(5387))
        TestWorld.tick()

        assertEquals(listOf("5387"), buttons)
    }

    @Test
    fun `a map pick is queued like the client's`() {
        agent()

        val view = api().act("agent_a", PlayerAction.PickTile(3086, 3233))

        assertEquals(ActionView("agent_a", "pick tile", "opcode 101 queued for the next tick"), view)
    }

    @Test
    fun `a map pick reaches the game as the picked tile on the next tick`() {
        agent()
        val picks = record(MapPickEvent::class.java) { "${it.tile}" }

        api().act("agent_a", PlayerAction.PickTile(3086, 3233))
        TestWorld.tick()

        assertEquals(listOf("Tile(x=3086, y=3233, z=0)"), picks)
    }

    @Test
    fun `a search page is queued like the client's`() {
        searching()

        val view = api().act("agent_a", PlayerAction.SearchPage(0, 6, "oak"))

        assertEquals(ActionView("agent_a", "search page", "opcode 102 queued for the next tick"), view)
    }

    @Test
    fun `a search page reaches the game with the open search's serial on the next tick`() {
        searching()
        val pages = record(SearchPageEvent::class.java) { "${it.serial} ${it.offset} ${it.count} ${it.query}" }

        api().act("agent_a", PlayerAction.SearchPage(3, 6, "oak logs"))
        TestWorld.tick()

        assertEquals(listOf("1 3 6 oak logs"), pages)
    }

    @Test
    fun `a search page with no search open is refused`() {
        agent()

        assertEquals(409, status { api().act("agent_a", PlayerAction.SearchPage(0, 6, "")) })
    }

    @Test
    fun `a search pick reaches the game with the open search's serial on the next tick`() {
        searching()
        val picks = record(SearchPickEvent::class.java) { "${it.serial} ${it.index}" }

        api().act("agent_a", PlayerAction.SearchPick(12))
        TestWorld.tick()

        assertEquals(listOf("1 12"), picks)
    }

    @Test
    fun `a search pick on a name prompt is refused`() {
        naming()

        assertEquals(409, status { api().act("agent_a", PlayerAction.SearchPick(0)) })
    }

    @Test
    fun `a name reaches the game with the open name prompt's serial on the next tick`() {
        naming()
        val names = record(SearchNameEvent::class.java) { "${it.serial} ${it.typed}" }

        api().act("agent_a", PlayerAction.SearchName("Willow chop"))
        TestWorld.tick()

        assertEquals(listOf("1 Willow chop"), names)
    }

    @Test
    fun `a name on a search is refused`() {
        searching()

        assertEquals(409, status { api().act("agent_a", PlayerAction.SearchName("Willow chop")) })
    }

    @Test
    fun `a search close reaches the game with the open prompt's serial on the next tick`() {
        naming()
        val closes = record(SearchClosedEvent::class.java) { "${it.serial}" }

        api().act("agent_a", PlayerAction.SearchClose)
        TestWorld.tick()

        assertEquals(listOf("1"), closes)
    }

    @Test
    fun `a search close with no prompt open is refused`() {
        agent()

        assertEquals(409, status { api().act("agent_a", PlayerAction.SearchClose) })
    }

    @Test
    fun `an amount reaches the game as the client's answer to the open prompt on the next tick`() {
        agent().overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) = Unit
        })
        val amounts = record(NumberInputEvent::class.java) { "${it.number}" }

        api().act("agent_a", PlayerAction.Amount(25))
        TestWorld.tick()

        assertEquals(listOf("25"), amounts)
    }

    @Test
    fun `an amount with no amount prompt open is refused`() {
        agent()

        assertEquals(409, status { api().act("agent_a", PlayerAction.Amount(25)) })
    }

    @Test
    fun `a drag reaches the game as the client's arrange packet on the next tick`() {
        agent()
        val moves = record(ArrangeItemEvent::class.java) { "${it.widgetId} ${it.fromIndex} ${it.toIndex} ${it.insertionMode}" }

        api().act("agent_a", PlayerAction.Arrange(30711, 0, 2))
        TestWorld.tick()

        assertEquals(listOf("30711 0 2 1"), moves)
    }

    @Test
    fun `continuing a dialogue is queued like the client's`() {
        agent()

        val view = api().act("agent_a", PlayerAction.ContinueDialogue)

        assertEquals(ActionView("agent_a", "continue dialogue", "opcode 226 queued for the next tick"), view)
    }

    @Test
    fun `closing the interface is queued like the client's`() {
        agent()

        val view = api().act("agent_a", PlayerAction.CloseInterface)

        assertEquals(ActionView("agent_a", "close interface", "opcode 110 queued for the next tick"), view)
    }

    @Test
    fun `closing the interface is handled on the next tick`() {
        agent()
        val closed = record(CloseInterfaceEvent::class.java) { it.plr.username }

        api().act("agent_a", PlayerAction.CloseInterface)
        TestWorld.tick()

        assertEquals(listOf("agent_a"), closed)
    }

    @Test
    fun `the content audit files a spawned npc under its zone, dated by the clock`() {
        TestWorld.spawnNpc(man, spawn)

        val lumbridge = apiOn("2026-10-02").contentAudit().files.getValue("lumbridge.txt")

        assertEquals("Content audit: lumbridge, 2026-10-02", lumbridge.lines().first())
    }

    @Test
    fun `a player's flow reads as its steps, each with its slot's lines, and its reflexes`() {
        val chop = step("chop", "oak").copy(id = 1, reflexes = listOf(3))
        val reflex = ReflexSettings(3, mapOf("do" to "stop"))
        agent().idleState = IdleState(steps = listOf(chop, step("drop").copy(id = 2)), reflexes = listOf(reflex), stepIndex = 1)

        val view = api().flow("agent_a")

        val steps = listOf(
            FlowStepView(1, "chop", mapOf("word" to "oak"), listOf(3), listOf("oak"), null, null, false, 0, emptyList()),
            FlowStepView(2, "drop", emptyMap(), emptyList(), emptyList(), null, null, false, 0, emptyList()),
        )
        assertEquals(
            FlowView("agent_a", running = false, stepIndex = 1, laps = 0, blocked = null, waiting = null, underWay = null, stop = FlowStopView("never", ""), travel = FlowTravelView(coins = false, runes = false, charges = false), steps = steps,
                reflexes = listOf(FlowReflexView(3, mapOf("do" to "stop"))),
            ),
            view,
        )
    }

    @Test
    fun `a flow's run reads its laps, its stop and each step's goal, whether met, and what it counted`() {
        val oaks = step("chop", "oak").copy(values = mapOf("word" to "oak", "goal" to "count", "goalCount" to "3"))
        agent().idleState = IdleState(steps = listOf(oaks, step("drop")), laps = 2, stop = StopSettings("laps", "5"), progress = RunProgress(totals = mapOf(0 to 2), met = setOf(0)))

        val view = api().flow("agent_a")

        assertEquals(listOf<Any?>(2, FlowStopView("laps", "5"), "Goal met: 3 in all", true, 2, listOf("oak", "Goal met: 3 in all"), null, false, 0), listOf(
            view.laps, view.stop, view.steps[0].goal, view.steps[0].met, view.steps[0].counted, view.steps[0].lines, view.steps[1].goal, view.steps[1].met, view.steps[1].counted,
        ))
    }

    @Test
    fun `a bank step's withdrawals left out for the run read on it`() {
        agent().idleState = IdleState(steps = listOf(step("light"), step("bank")), running = true, progress = RunProgress(met = setOf(0)))

        val view = api().flow("agent_a")

        assertEquals(listOf(emptyList(), listOf(1521)), view.steps.map { it.leftOut })
    }

    @Test
    fun `replacing a flow takes its stop`() {
        val player = agent()

        api().replaceFlow("agent_a", listOf(step("drop")), emptyList(), StopSettings("time", "30"), TravelAllowance.NONE)

        assertEquals(StopSettings("time", "30"), player.idleState.stop)
    }

    @Test
    fun `replacing a flow takes what its trips may spend`() {
        val player = agent()

        api().replaceFlow("agent_a", listOf(step("drop")), emptyList(), StopSettings(), TravelAllowance(coins = true, charges = true))

        assertEquals(TravelAllowance(coins = true, charges = true), player.idleState.travel)
    }

    @Test
    fun `a flow reads what its trips may spend`() {
        agent().idleState = IdleState(steps = listOf(step("drop")), travel = TravelAllowance(runes = true))

        assertEquals(FlowTravelView(coins = false, runes = true, charges = false), api().flow("agent_a").travel)
    }

    @Test
    fun `a flow with a stop the builder would refuse is a 400 with the reason`() {
        agent()

        val thrown = assertThrows<HarnessException> { api().replaceFlow("agent_a", listOf(step("drop")), emptyList(), StopSettings("laps", "0"), TravelAllowance.NONE) }

        assertEquals(400 to "Laps take 1 or more, not '0'", thrown.status to thrown.message)
    }

    @Test
    fun `a flow's running block and each step's problem read as the builder shows them`() {
        agent().idleState = IdleState(steps = listOf(step("chop", "oak"), step("chop", "bad")), running = true, blocked = "Needs an axe you can use", waiting = "No tree in reach")

        val view = api().flow("agent_a")

        assertEquals(listOf("Needs an axe you can use", "No tree in reach", listOf(null, "'bad' is refused")), listOf(view.blocked, view.waiting, view.steps.map { it.problem }))
    }

    @Test
    fun `what a running flow's trip does on its way reads as the overlay shows it`() {
        agent().idleState = IdleState(steps = listOf(step("chop", "oak")), running = true, underWay = "Opening the door")

        assertEquals("Opening the door", api().flow("agent_a").underWay)
    }

    @Test
    fun `replacing a flow sets it from its first step, stopped, and answers it`() {
        val player = agent()
        player.idleState = IdleState(steps = listOf(step("drop")), stepIndex = 1, laps = 3, stage = 2)

        val view = api().replaceFlow("agent_a", listOf(step("chop", "oak").copy(id = 1), step("drop").copy(id = 2)), emptyList(), StopSettings(), TravelAllowance.NONE)

        assertEquals(IdleState(steps = listOf(step("chop", "oak").copy(id = 1), step("drop").copy(id = 2)), stage = 2), player.idleState)
        assertEquals(listOf(listOf("oak"), emptyList()), view.steps.map { it.lines })
    }

    @Test
    fun `replacing a flow gives its steps and reflexes without an id one`() {
        val player = agent()
        val fight = step("chop").copy(reflexes = listOf(1))

        api().replaceFlow("agent_a", listOf(fight, step("drop").copy(id = 1)), listOf(ReflexSettings()), StopSettings(), TravelAllowance.NONE)

        assertEquals(listOf(2, 1), player.idleState.steps.map { it.id })
        assertEquals(listOf(1), player.idleState.reflexes.map { it.id })
    }

    @Test
    fun `a flow with a reflex the builder would refuse is a 400 with the reason`() {
        agent()

        val thrown = assertThrows<HarnessException> {
            api().replaceFlow("agent_a", listOf(step("drop")), listOf(ReflexSettings(1, mapOf("do" to "dance"))), StopSettings(), TravelAllowance.NONE)
        }

        assertEquals(400 to "Reflex 1: 'dance' is not something a reflex does", thrown.status to thrown.message)
    }

    @Test
    fun `a running flow is not replaced`() {
        agent().idleState = IdleState(steps = listOf(step("drop")), running = true)

        assertEquals(409, status { api().replaceFlow("agent_a", emptyList(), emptyList(), StopSettings(), TravelAllowance.NONE) })
    }

    @Test
    fun `a flow the builder would refuse is a 400 with the reason`() {
        val player = agent()

        val thrown = assertThrows<HarnessException> { api().replaceFlow("agent_a", listOf(step("chop", "bad")), emptyList(), StopSettings(), TravelAllowance.NONE) }

        assertEquals(400 to "Step 1: 'bad' is refused", thrown.status to thrown.message)
        assertEquals(IdleState(), player.idleState)
    }

    @Test
    fun `a bot has no flow to replace`() {
        TestWorld.bot("botty", spawn)

        assertEquals(409, status { api().replaceFlow("botty", emptyList(), emptyList(), StopSettings(), TravelAllowance.NONE) })
    }

    @Test
    fun `the flow of a player not online is a 404`() {
        assertEquals(404, status { api().flow("nobody") })
    }

    private fun step(kind: String, word: String? = null) = FakeStepType.step(kind, word)
}
