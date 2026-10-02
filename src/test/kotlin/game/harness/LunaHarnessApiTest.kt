package game.harness

import game.idle.IdleState
import game.idle.idleState
import game.testworld.TestWorld
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.event.Event
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.CloseInterfaceEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
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

class LunaHarnessApiTest {

    private val spawn = Position(3200, 3200)
    private val boothTile = Position(3201, 3200)
    private val booth = 2213
    private val inertBooth = 2214
    private val normalTree = 1276
    private val man = 1
    private val bronzeAxe = 1351
    private val logs = 1511

    private class Endless(player: Player) : Action<Player>(player, ActionType.WEAK) {
        override fun run(): Boolean = false
    }

    private fun api() = LunaHarnessApi(
        TestWorld.world,
        LunaGameThread(TestWorld.context.game, Duration.ofSeconds(1)),
        HeadlessPlayers(TestWorld.context, HarnessConfig()) { CompletableFuture.completedFuture(null) },
    )

    private fun apiOn(date: String) = LunaHarnessApi(
        TestWorld.world,
        LunaGameThread(TestWorld.context.game, Duration.ofSeconds(1)),
        HeadlessPlayers(TestWorld.context, HarnessConfig()) { CompletableFuture.completedFuture(null) },
        clock = Clock.fixed(Instant.parse("${date}T12:00:00Z"), ZoneOffset.UTC),
    )

    private fun agent(position: Position = spawn): Player = TestWorld.login("agent_a", position)

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

        val view = api().act("agent_a", PlayerAction.Command("::flow show"))

        assertEquals(ActionView("agent_a", "command", "opcode 56 queued for the next tick"), view)
    }

    @Test
    fun `a command is handled on the next tick as if typed`() {
        agent()
        val commands = record(CommandEvent::class.java) { "${it.name} ${it.args.joinToString()}" }

        api().act("agent_a", PlayerAction.Command("::flow show"))
        TestWorld.tick()

        assertEquals(listOf("flow show"), commands)
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
}
