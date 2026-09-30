package game.harness

import game.testworld.TestWorld
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

    @Test
    fun `the Luna game thread runs a task on the game thread and returns its value`() {
        val gameThread = LunaGameThread(TestWorld.context.game, Duration.ofSeconds(1))

        assertEquals(TestWorld.context.game.thread, gameThread.run { Thread.currentThread() })
    }

    @Test
    fun `the Luna game thread waits for a future`() {
        val gameThread = LunaGameThread(TestWorld.context.game, Duration.ofSeconds(1))

        assertEquals("done", gameThread.await(CompletableFuture.completedFuture("done")))
    }
}
