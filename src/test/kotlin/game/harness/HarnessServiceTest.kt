package game.harness

import io.luna.net.msg.out.GameChatboxMessageWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HarnessServiceTest {

    private val configsSeen = mutableListOf<HarnessConfig>()
    private val service = HarnessService { config ->
        configsSeen += config
        FakeHarnessApi()
    }
    private val enabled = HarnessConfig(enabled = true, port = 0)

    @AfterEach
    fun stopService() {
        service.stop()
    }

    private fun statusOf(port: Int, path: String): Int {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    @Test
    fun `a disabled config starts nothing`() {
        val address = service.start(HarnessConfig(enabled = false))

        assertNull(address)
    }

    @Test
    fun `a disabled config never builds the game side`() {
        service.start(HarnessConfig(enabled = false))

        assertEquals(emptyList<HarnessConfig>(), configsSeen)
    }

    @Test
    fun `an enabled config builds the game side from that config`() {
        service.start(enabled)

        assertEquals(listOf(enabled), configsSeen)
    }

    @Test
    fun `an enabled config serves the harness endpoints`() {
        val address = service.start(enabled)

        assertEquals(200, statusOf(checkNotNull(address).port, "/world"))
    }

    @Test
    fun `starting twice is refused`() {
        service.start(enabled)

        assertThrows<IllegalStateException> { service.start(enabled) }
    }

    @Test
    fun `a login while the harness runs taps the client channel`() {
        val channel = loggedInChannel()
        service.start(enabled)

        service.onLogin(channel) { 0 }

        assertNotNull(MessageTap.of(channel))
    }

    @Test
    fun `a login while the harness is off taps nothing`() {
        val channel = loggedInChannel()
        service.start(HarnessConfig(enabled = false))

        service.onLogin(channel) { 0 }

        assertNull(MessageTap.of(channel))
    }

    @Test
    fun `a login after the harness stopped taps nothing`() {
        val channel = loggedInChannel()
        service.start(enabled)
        service.stop()

        service.onLogin(channel) { 0 }

        assertNull(MessageTap.of(channel))
    }

    @Test
    fun `a tapped channel keeps as many messages as the config says`() {
        val channel = loggedInChannel()
        service.start(enabled.copy(messageBufferSize = 1))
        service.onLogin(channel) { 0 }

        channel.writeOutbound(encode(GameChatboxMessageWriter("first")), encode(GameChatboxMessageWriter("second")))

        assertEquals(listOf(2L), MessageTap.of(channel)?.log?.since(0)?.messages?.map { it.seq })
    }

    @Test
    fun `a stopped service can start again`() {
        service.start(enabled)
        service.stop()

        val address = service.start(enabled)

        assertEquals(200, statusOf(checkNotNull(address).port, "/world"))
    }
}
