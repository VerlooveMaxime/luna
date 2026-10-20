package game.harness

import game.idle.flow.FlowIds
import game.idle.flow.FlowStops
import game.idle.flow.ReflexSettings
import game.idle.flow.StepSettings
import game.idle.flow.StopSettings
import game.idle.movement.TravelAllowance
import game.idle.ui.SearchPrompt

/** The endpoint table. `GET /` lists it, so an agent can discover the API from the server itself. */
fun harnessRoutes(api: HarnessApi): List<Route> {

    fun action(path: String, parse: (RequestBody) -> PlayerAction) =
        Route("POST", "/player/{name}/$path") { call -> api.act(call.path("name"), parse(call.body())) }

    fun bodilessAction(path: String, action: PlayerAction) =
        Route("POST", "/player/{name}/$path") { call -> api.act(call.path("name"), action) }

    val endpoints = listOf(
        Route("GET", "/world") { api.world() },
        Route("GET", "/players") { call -> api.players(includeBots = call.queryBoolean("bots", default = true)) },
        Route("POST", "/login") { call -> api.login(call.body().string("name")) },
        Route("POST", "/logout") { call -> api.logout(call.body().string("name")) },
        Route("GET", "/player/{name}") { call -> api.player(call.path("name")) },
        Route("GET", "/player/{name}/nearby") { call ->
            api.nearby(
                name = call.path("name"),
                radius = call.queryInt("radius", default = 8),
                includeInert = call.queryBoolean("all", default = false),
            )
        },
        Route("GET", "/player/{name}/tile") { call ->
            api.tile(call.path("name"), call.queryInt("x"), call.queryInt("y"), call.queryIntOrNull("z"))
        },
        Route("GET", "/player/{name}/messages") { call ->
            api.messages(
                name = call.path("name"),
                since = call.queryLong("since", default = 0),
                type = call.request.query["type"],
            )
        },
        action("click/object") { body ->
            PlayerAction.ClickObject(body.int("x"), body.int("y"), body.int("id"), body.int("option", default = 1))
        },
        action("click/npc") { body -> PlayerAction.ClickNpc(body.int("index"), body.int("option", default = 1)) },
        action("click/item") { body ->
            PlayerAction.ClickItem(body.int("slot"), body.intOrNull("id"), body.int("option", default = 1))
        },
        action("click/ground") { body ->
            PlayerAction.ClickGroundItem(body.int("x"), body.int("y"), body.int("id"), body.int("option", default = 3))
        },
        action("walk") { body -> PlayerAction.Walk(body.int("x"), body.int("y")) },
        action("command") { body -> PlayerAction.Command(body.string("text")) },
        action("chat") { body -> PlayerAction.Chat(body.string("text")) },
        action("button") { body -> PlayerAction.Button(body.int("id")) },
        action("map/pick") { body -> PlayerAction.PickTile(body.int("x"), body.int("y")) },
        action("search/page") { body ->
            PlayerAction.SearchPage(
                body.int("offset", default = 0),
                body.int("count", default = SearchPrompt.MAX_PAGE),
                body.string("query", default = ""),
            )
        },
        action("search/pick") { body -> PlayerAction.SearchPick(body.int("index")) },
        action("search/name") { body -> PlayerAction.SearchName(body.string("text")) },
        bodilessAction("search/close", PlayerAction.SearchClose),
        action("amount") { body -> PlayerAction.Amount(body.int("value")) },
        action("arrange") { body -> PlayerAction.Arrange(body.int("widget"), body.int("from"), body.int("to")) },
        bodilessAction("continue", PlayerAction.ContinueDialogue),
        bodilessAction("close", PlayerAction.CloseInterface),
        Route("GET", "/content/audit") { api.contentAudit() },
        Route("GET", "/player/{name}/flow") { call -> api.flow(call.path("name")) },
        Route("POST", "/player/{name}/flow") { call ->
            val body = call.body()
            val steps = body.objects("steps").map { step ->
                StepSettings(step.string("kind"), settings(step), step.int("id", FlowIds.NONE), step.ints("reflexes"))
            }
            val reflexes = body.optionalObjects("reflexes").map { reflex -> ReflexSettings(reflex.int("id", FlowIds.NONE), settings(reflex)) }
            api.replaceFlow(call.path("name"), steps, reflexes, stop(body), travel(body))
        },
    )
    val index = Route("GET", "/") { endpoints.map { "${it.method} ${it.pattern}" } }
    return listOf(index) + endpoints
}

/** A posted step's or reflex's settings, read as text; an empty value is never kept. */
private fun settings(body: RequestBody): Map<String, String> = body.strings("values").filterValues { it.isNotEmpty() }

/** A posted flow's stop (S12), never without one: `{"kind": "laps", "after": 5}`, both read as text. */
private fun stop(body: RequestBody): StopSettings {
    val stop = body.strings("stop")
    return StopSettings(stop["kind"] ?: FlowStops.NEVER, stop["after"].orEmpty())
}

/**
 * What a posted flow's trips may spend: `{"coins": true, "runes": false, "charges": true, "bank": true}`, each read as
 * text and off when left out, as is a missing object.
 */
private fun travel(body: RequestBody): TravelAllowance {
    val travel = body.strings("travel")
    val unknown = travel.keys - TRAVEL_CHOICES
    if (unknown.isNotEmpty()) throw HarnessException(400, "field 'travel' takes ${TRAVEL_CHOICES.joinToString()}, not '${unknown.first()}'")
    fun allowed(choice: String): Boolean =
        when (travel[choice]?.lowercase()) {
            null, "false" -> false
            "true" -> true
            else -> throw HarnessException(400, "travel's '$choice' must be true or false")
        }
    return TravelAllowance(coins = allowed("coins"), runes = allowed("runes"), charges = allowed("charges"), bank = allowed("bank"))
}

private val TRAVEL_CHOICES = listOf("coins", "runes", "charges", "bank")
