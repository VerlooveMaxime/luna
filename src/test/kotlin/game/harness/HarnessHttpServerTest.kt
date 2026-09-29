package game.harness

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HarnessHttpServerTest {

    private val received = mutableListOf<HarnessRequest>()

    private val router = HarnessRouter(
        listOf(
            Route("GET", "/echo") { call ->
                received += call.request
                mapOf("radius" to call.queryInt("radius", default = 0))
            },
            Route("POST", "/echo") { call ->
                received += call.request
                mapOf("name" to call.body().string("name"))
            },
        ),
    )

    private val server = HarnessHttpServer.start(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), router)
    private val client = HttpClient.newHttpClient()

    @AfterEach
    fun stopServer() {
        server.stop()
    }

    private fun uri(pathAndQuery: String) = URI.create("http://127.0.0.1:${server.address.port}$pathAndQuery")

    private fun send(request: HttpRequest): HttpResponse<String> =
        client.send(request, HttpResponse.BodyHandlers.ofString())

    @Test
    fun `answers with the router status and a JSON body`() {
        val response = send(HttpRequest.newBuilder(uri("/echo?radius=5")).GET().build())

        assertEquals(200, response.statusCode())
        assertEquals("{\n  \"radius\": 5\n}\n", response.body())
    }

    @Test
    fun `labels the answer as JSON`() {
        val response = send(HttpRequest.newBuilder(uri("/echo")).GET().build())

        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""))
    }

    @Test
    fun `hands the request body and method to the router`() {
        val body = HttpRequest.BodyPublishers.ofString("""{"name":"agent_a"}""")

        send(HttpRequest.newBuilder(uri("/echo")).POST(body).build())

        assertEquals(HarnessRequest("POST", "/echo", emptyMap(), """{"name":"agent_a"}"""), received.single())
    }

    @Test
    fun `sends router errors with their status`() {
        val response = send(HttpRequest.newBuilder(uri("/missing")).GET().build())

        assertEquals(404, response.statusCode())
    }
}
