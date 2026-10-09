package game.harness

import game.idle.flow.StepSettings
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
        bodilessAction("continue", PlayerAction.ContinueDialogue),
        bodilessAction("close", PlayerAction.CloseInterface),
        Route("GET", "/content/audit") { api.contentAudit() },
        Route("GET", "/player/{name}/flow") { call -> api.flow(call.path("name")) },
        Route("POST", "/player/{name}/flow") { call ->
            val steps = call.body().objects("steps").map { step ->
                StepSettings(step.string("kind"), step.strings("values").filterValues { it.isNotEmpty() })
            }
            api.replaceFlow(call.path("name"), steps)
        },
    )
    val index = Route("GET", "/") { endpoints.map { "${it.method} ${it.pattern}" } }
    return listOf(index) + endpoints
}
