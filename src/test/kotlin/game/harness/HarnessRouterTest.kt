package game.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HarnessRouterTest {

    private fun call(query: Map<String, String> = emptyMap(), body: String = "") =
        RouteCall(HarnessRequest("GET", "/", query, body), pathParams = emptyMap())

    private fun routerAnswering(handler: (RouteCall) -> Any) = HarnessRouter(listOf(Route("GET", "/thing", handler)))

    @Test
    fun `route matches an identical literal path`() {
        val route = Route("GET", "/world") { "ok" }

        assertEquals(emptyMap<String, String>(), route.match("/world"))
    }

    @Test
    fun `route ignores a trailing slash`() {
        val route = Route("GET", "/world") { "ok" }

        assertEquals(emptyMap<String, String>(), route.match("/world/"))
    }

    @Test
    fun `route captures path parameters`() {
        val route = Route("GET", "/player/{name}/nearby") { "ok" }

        assertEquals(mapOf("name" to "agent_a"), route.match("/player/agent_a/nearby"))
    }

    @Test
    fun `route rejects a path with a different segment count`() {
        val route = Route("GET", "/player/{name}") { "ok" }

        assertNull(route.match("/player/agent_a/nearby"))
    }

    @Test
    fun `route rejects a different literal segment`() {
        val route = Route("GET", "/player/{name}/nearby") { "ok" }

        assertNull(route.match("/player/agent_a/messages"))
    }

    @Test
    fun `router answers 200 with the handler result`() {
        val router = routerAnswering { "hello" }

        assertEquals(HarnessResponse(200, "hello"), router.handle(HarnessRequest("GET", "/thing")))
    }

    @Test
    fun `router passes path parameters to the handler`() {
        val router = HarnessRouter(listOf(Route("GET", "/player/{name}") { it.path("name") }))

        assertEquals(HarnessResponse(200, "agent_a"), router.handle(HarnessRequest("GET", "/player/agent_a")))
    }

    @Test
    fun `router answers 404 for an unknown path`() {
        val router = routerAnswering { "hello" }

        val response = router.handle(HarnessRequest("GET", "/other"))

        assertEquals(HarnessResponse(404, ErrorView("no endpoint at /other")), response)
    }

    @Test
    fun `router answers 405 naming the accepted methods`() {
        val router = routerAnswering { "hello" }

        val response = router.handle(HarnessRequest("POST", "/thing"))

        assertEquals(HarnessResponse(405, ErrorView("/thing accepts GET")), response)
    }

    @Test
    fun `router turns a harness exception into its status`() {
        val router = routerAnswering { throw HarnessException(409, "busy") }

        assertEquals(HarnessResponse(409, ErrorView("busy")), router.handle(HarnessRequest("GET", "/thing")))
    }

    @Test
    fun `router answers 400 for a malformed JSON body`() {
        val router = routerAnswering { it.body() }

        val response = router.handle(HarnessRequest("GET", "/thing", body = "{\"x\": "))

        assertEquals(400, response.status)
    }

    @Test
    fun `router answers 500 for an unexpected failure`() {
        val router = routerAnswering { error("broken") }

        assertEquals(
            HarnessResponse(500, ErrorView("IllegalStateException: broken")),
            router.handle(HarnessRequest("GET", "/thing")),
        )
    }

    @Test
    fun `path fails loudly for a parameter the route does not declare`() {
        assertThrows<IllegalStateException> { call().path("name") }
    }

    @Test
    fun `query int falls back to the default`() {
        assertEquals(8, call().queryInt("radius", default = 8))
    }

    @Test
    fun `query int parses the value`() {
        assertEquals(12, call(query = mapOf("radius" to "12")).queryInt("radius", default = 8))
    }

    @Test
    fun `query int rejects a non-number`() {
        val thrown = assertThrows<HarnessException> { call(query = mapOf("radius" to "far")).queryInt("radius", 8) }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `query long falls back to the default`() {
        assertEquals(0L, call().queryLong("since", default = 0))
    }

    @Test
    fun `query long parses the value`() {
        assertEquals(9_000_000_000L, call(query = mapOf("since" to "9000000000")).queryLong("since", default = 0))
    }

    @Test
    fun `query long rejects a non-number`() {
        val thrown = assertThrows<HarnessException> { call(query = mapOf("since" to "x")).queryLong("since", 0) }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `query boolean falls back to the default`() {
        assertTrue(call().queryBoolean("bots", default = true))
    }

    @Test
    fun `query boolean reads true`() {
        assertTrue(call(query = mapOf("all" to "TRUE")).queryBoolean("all", default = false))
    }

    @Test
    fun `query boolean reads a bare key as true`() {
        assertTrue(call(query = mapOf("all" to "")).queryBoolean("all", default = false))
    }

    @Test
    fun `query boolean reads 0 as false`() {
        assertFalse(call(query = mapOf("bots" to "0")).queryBoolean("bots", default = true))
    }

    @Test
    fun `query boolean rejects other values`() {
        val call = call(query = mapOf("bots" to "maybe"))

        val thrown = assertThrows<HarnessException> { call.queryBoolean("bots", default = true) }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `body is required`() {
        val thrown = assertThrows<HarnessException> { call(body = " ").body() }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `body must be an object`() {
        val thrown = assertThrows<HarnessException> { call(body = "[1, 2]").body() }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `body int reads an integer field`() {
        assertEquals(3171, call(body = """{"x": 3171}""").body().int("x"))
    }

    @Test
    fun `body int rejects a missing field`() {
        val thrown = assertThrows<HarnessException> { call(body = "{}").body().int("x") }

        assertEquals("field 'x' is required and must be an integer", thrown.message)
    }

    @Test
    fun `body int with a default uses it for a missing field`() {
        assertEquals(1, call(body = "{}").body().int("option", default = 1))
    }

    @Test
    fun `body int treats JSON null as missing`() {
        assertNull(call(body = """{"id": null}""").body().intOrNull("id"))
    }

    @Test
    fun `body int rejects a fraction`() {
        val thrown = assertThrows<HarnessException> { call(body = """{"x": 1.5}""").body().int("x") }

        assertEquals("field 'x' must be an integer", thrown.message)
    }

    @Test
    fun `body int rejects a string`() {
        val thrown = assertThrows<HarnessException> { call(body = """{"x": "1"}""").body().int("x") }

        assertEquals("field 'x' must be an integer", thrown.message)
    }

    @Test
    fun `body rejects a nested value where a single value is expected`() {
        val thrown = assertThrows<HarnessException> { call(body = """{"x": {"y": 1}}""").body().int("x") }

        assertEquals("field 'x' must be a single value", thrown.message)
    }

    @Test
    fun `body string reads a string field`() {
        assertEquals("agent_a", call(body = """{"name": "agent_a"}""").body().string("name"))
    }

    @Test
    fun `body string rejects a missing field`() {
        val thrown = assertThrows<HarnessException> { call(body = "{}").body().string("name") }

        assertEquals("field 'name' is required and must be a string", thrown.message)
    }

    @Test
    fun `body string rejects a number`() {
        val thrown = assertThrows<HarnessException> { call(body = """{"name": 5}""").body().string("name") }

        assertEquals("field 'name' must be a string", thrown.message)
    }

    @Test
    fun `parse query of nothing is empty`() {
        assertEquals(emptyMap<String, String>(), parseQuery(null))
    }

    @Test
    fun `parse query of an empty string is empty`() {
        assertEquals(emptyMap<String, String>(), parseQuery(""))
    }

    @Test
    fun `parse query decodes each pair`() {
        assertEquals(mapOf("radius" to "8", "type" to "chat box"), parseQuery("radius=8&type=chat%20box"))
    }

    @Test
    fun `parse query skips empty segments`() {
        assertEquals(mapOf("radius" to "8"), parseQuery("&radius=8&&"))
    }

    @Test
    fun `parse query maps a bare key to an empty value`() {
        assertEquals(mapOf("all" to ""), parseQuery("all"))
    }
}
