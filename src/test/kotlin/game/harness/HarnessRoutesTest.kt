package game.harness

import game.idle.flow.ReflexSettings
import game.idle.flow.StepSettings
import game.idle.ui.SearchPrompt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HarnessRoutesTest {

    private val api = FakeHarnessApi()
    private val router = HarnessRouter(harnessRoutes(api))

    private fun get(path: String, query: Map<String, String> = emptyMap()) =
        router.handle(HarnessRequest("GET", path, query))

    private fun post(path: String, body: String = "") = router.handle(HarnessRequest("POST", path, body = body))

    private fun lastAction(): Pair<String, PlayerAction> = api.actions.last()

    @Test
    fun `index lists every endpoint with its method`() {
        val listed = get("/").body as List<*>

        assertEquals(harnessRoutes(api).drop(1).map { "${it.method} ${it.pattern}" }, listed)
    }

    @Test
    fun `world answers the api view`() {
        assertEquals(HarnessResponse(200, api.worldView), get("/world"))
    }

    @Test
    fun `content audit answers the report files`() {
        assertEquals(HarnessResponse(200, api.contentAuditView), get("/content/audit"))
    }

    @Test
    fun `players include bots by default`() {
        get("/players")

        assertEquals(listOf("players bots=true"), api.calls)
    }

    @Test
    fun `players can leave bots out`() {
        get("/players", mapOf("bots" to "false"))

        assertEquals(listOf("players bots=false"), api.calls)
    }

    @Test
    fun `login passes the requested name`() {
        post("/login", """{"name": "agent_a"}""")

        assertEquals(listOf("login agent_a"), api.calls)
    }

    @Test
    fun `logout passes the requested name`() {
        post("/logout", """{"name": "agent_a"}""")

        assertEquals(listOf("logout agent_a"), api.calls)
    }

    @Test
    fun `player reads the name from the path`() {
        get("/player/agent_a")

        assertEquals(listOf("player agent_a"), api.calls)
    }

    @Test
    fun `nearby defaults to radius 8 without inert objects`() {
        get("/player/agent_a/nearby")

        assertEquals(listOf("nearby agent_a radius=8 all=false"), api.calls)
    }

    @Test
    fun `nearby reads radius and all from the query`() {
        get("/player/agent_a/nearby", mapOf("radius" to "15", "all" to "true"))

        assertEquals(listOf("nearby agent_a radius=15 all=true"), api.calls)
    }

    @Test
    fun `messages default to everything since the start`() {
        get("/player/agent_a/messages")

        assertEquals(listOf("messages agent_a since=0 type=null"), api.calls)
    }

    @Test
    fun `messages read since and type from the query`() {
        get("/player/agent_a/messages", mapOf("since" to "40", "type" to "chatbox"))

        assertEquals(listOf("messages agent_a since=40 type=chatbox"), api.calls)
    }

    @Test
    fun `object click defaults to option 1`() {
        post("/player/agent_a/click/object", """{"x": 3171, "y": 3444, "id": 1278}""")

        assertEquals("agent_a" to PlayerAction.ClickObject(3171, 3444, 1278, option = 1), lastAction())
    }

    @Test
    fun `npc click reads index and option`() {
        post("/player/agent_a/click/npc", """{"index": 8840, "option": 3}""")

        assertEquals("agent_a" to PlayerAction.ClickNpc(8840, option = 3), lastAction())
    }

    @Test
    fun `item click may leave the id out`() {
        post("/player/agent_a/click/item", """{"slot": 7, "option": 2}""")

        assertEquals("agent_a" to PlayerAction.ClickItem(7, id = null, option = 2), lastAction())
    }

    @Test
    fun `item click passes an id to check`() {
        post("/player/agent_a/click/item", """{"slot": 9, "id": 1511, "option": 5}""")

        assertEquals("agent_a" to PlayerAction.ClickItem(9, id = 1511, option = 5), lastAction())
    }

    @Test
    fun `ground item click defaults to option 3, the client's Take`() {
        post("/player/agent_a/click/ground", """{"x": 3172, "y": 3443, "id": 1511}""")

        assertEquals("agent_a" to PlayerAction.ClickGroundItem(3172, 3443, 1511, option = 3), lastAction())
    }

    @Test
    fun `walk reads the destination`() {
        post("/player/agent_a/walk", """{"x": 3200, "y": 3201}""")

        assertEquals("agent_a" to PlayerAction.Walk(3200, 3201), lastAction())
    }

    @Test
    fun `command passes the text`() {
        post("/player/agent_a/command", """{"text": "::move 3200 3200"}""")

        assertEquals("agent_a" to PlayerAction.Command("::move 3200 3200"), lastAction())
    }

    @Test
    fun `chat passes the text`() {
        post("/player/agent_a/chat", """{"text": "hi"}""")

        assertEquals("agent_a" to PlayerAction.Chat("hi"), lastAction())
    }

    @Test
    fun `a map pick passes the tile`() {
        post("/player/agent_a/map/pick", """{"x": 3086, "y": 3233}""")

        assertEquals("agent_a" to PlayerAction.PickTile(3086, 3233), lastAction())
    }

    @Test
    fun `a search page passes its offset, count and query`() {
        post("/player/agent_a/search/page", """{"offset": 30, "count": 15, "query": "oak"}""")

        assertEquals("agent_a" to PlayerAction.SearchPage(30, 15, "oak"), lastAction())
    }

    @Test
    fun `a search page asks for the opening rows by default`() {
        post("/player/agent_a/search/page", "{}")

        assertEquals("agent_a" to PlayerAction.SearchPage(0, SearchPrompt.MAX_PAGE, ""), lastAction())
    }

    @Test
    fun `a search pick passes the row's index`() {
        post("/player/agent_a/search/pick", """{"index": 12}""")

        assertEquals("agent_a" to PlayerAction.SearchPick(12), lastAction())
    }

    @Test
    fun `a search name passes the typed text`() {
        post("/player/agent_a/search/name", """{"text": "Willow chop"}""")

        assertEquals("agent_a" to PlayerAction.SearchName("Willow chop"), lastAction())
    }

    @Test
    fun `a search close needs no body`() {
        post("/player/agent_a/search/close")

        assertEquals("agent_a" to PlayerAction.SearchClose, lastAction())
    }

    @Test
    fun `an amount passes the number typed`() {
        post("/player/agent_a/amount", """{"value": 25}""")

        assertEquals("agent_a" to PlayerAction.Amount(25), lastAction())
    }

    @Test
    fun `arrange passes the layer and both places`() {
        post("/player/agent_a/arrange", """{"widget": 30711, "from": 0, "to": 2}""")

        assertEquals("agent_a" to PlayerAction.Arrange(30711, 0, 2), lastAction())
    }

    @Test
    fun `button passes the id`() {
        post("/player/agent_a/button", """{"id": 2482}""")

        assertEquals("agent_a" to PlayerAction.Button(2482), lastAction())
    }

    @Test
    fun `continue needs no body`() {
        post("/player/agent_a/continue")

        assertEquals("agent_a" to PlayerAction.ContinueDialogue, lastAction())
    }

    @Test
    fun `close needs no body`() {
        post("/player/agent_a/close")

        assertEquals("agent_a" to PlayerAction.CloseInterface, lastAction())
    }

    @Test
    fun `an action with a missing field answers 400 without reaching the api`() {
        val response = post("/player/agent_a/walk", """{"x": 3200}""")

        assertEquals(HarnessResponse(400, ErrorView("field 'y' is required and must be an integer")), response)
    }

    @Test
    fun `flow reads the name from the path`() {
        assertEquals(HarnessResponse(200, api.flowView), get("/player/agent_a/flow"))
        assertEquals(listOf("flow agent_a"), api.calls)
    }

    @Test
    fun `a flow is posted as steps of a kind and settings, values read as text, empty ones left out`() {
        val body = """{"steps": [{"kind": "chop", "values": {"tree": "oak", "amount": 5, "within": ""}}, {"kind": "drop"}]}"""

        val response = post("/player/agent_a/flow", body)

        val steps = listOf(StepSettings("chop", mapOf("tree" to "oak", "amount" to "5")), StepSettings("drop"))
        assertEquals(HarnessResponse(200, api.flowView), response)
        assertEquals(listOf("agent_a" to steps), api.replacedFlows)
    }

    @Test
    fun `a posted flow carries its steps' ids and attached reflexes, and its reflexes`() {
        val body = """{"steps": [{"id": 4, "kind": "fight", "reflexes": [2, 1]}], "reflexes": [{"id": 1, "values": {"do": "eat"}}, {"values": {"do": "run", "then": ""}}]}"""

        post("/player/agent_a/flow", body)

        assertEquals(listOf("agent_a" to listOf(StepSettings("fight", id = 4, reflexes = listOf(2, 1)))), api.replacedFlows)
        assertEquals(listOf(listOf(ReflexSettings(1, mapOf("do" to "eat")), ReflexSettings(0, mapOf("do" to "run")))), api.replacedReflexes)
    }

    @Test
    fun `a flow posted without reflexes has none`() {
        post("/player/agent_a/flow", """{"steps": []}""")

        assertEquals(listOf(emptyList<ReflexSettings>()), api.replacedReflexes)
    }

    @Test
    fun `a posted step without a kind answers 400`() {
        val response = post("/player/agent_a/flow", """{"steps": [{"values": {}}]}""")

        assertEquals(HarnessResponse(400, ErrorView("field 'kind' is required and must be a string")), response)
    }
}
