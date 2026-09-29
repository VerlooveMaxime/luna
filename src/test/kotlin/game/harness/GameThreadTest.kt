package game.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.CompletableFuture

class GameThreadTest {

    @Test
    fun `await returns the value of a completed future`() {
        val future = CompletableFuture.completedFuture("done")

        assertEquals("done", awaitWithin(future, Duration.ofSeconds(1)))
    }

    @Test
    fun `await rethrows the cause of a failed future`() {
        val future = CompletableFuture.failedFuture<String>(HarnessException(409, "busy"))

        val thrown = assertThrows<HarnessException> { awaitWithin(future, Duration.ofSeconds(1)) }

        assertEquals(409, thrown.status)
    }

    @Test
    fun `await answers 504 when the future does not complete in time`() {
        val future = CompletableFuture<String>()

        val thrown = assertThrows<HarnessException> { awaitWithin(future, Duration.ofMillis(1)) }

        assertEquals(504, thrown.status)
    }
}
