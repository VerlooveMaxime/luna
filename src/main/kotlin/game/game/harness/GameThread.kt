package game.harness

import io.luna.game.GameService
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

/** Runs harness work where game state may be touched, and waits for asynchronous game work from an HTTP thread. */
interface GameThread {

    fun <T> run(task: () -> T): T

    fun <T> await(future: CompletableFuture<T>): T
}

/** Hops onto Luna's game thread through [GameService.sync] and blocks the calling HTTP thread for the answer. */
class LunaGameThread(private val service: GameService, private val timeout: Duration) : GameThread {

    override fun <T> run(task: () -> T): T = await(service.sync(Supplier { task() }))

    override fun <T> await(future: CompletableFuture<T>): T = awaitWithin(future, timeout)
}

/** Waits for [future], unwrapping its failure and turning a timeout into a 504 answer. */
fun <T> awaitWithin(future: CompletableFuture<T>, timeout: Duration): T =
    try {
        future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    } catch (e: TimeoutException) {
        throw HarnessException(status = 504, message = "the game did not answer within ${timeout.toMillis()} ms")
    }
