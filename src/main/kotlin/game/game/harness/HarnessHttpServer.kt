package game.harness

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.luna.util.GsonUtils
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** JDK HTTP server that hands every request to a [HarnessRouter] and writes its answer as JSON. */
class HarnessHttpServer private constructor(
    private val server: HttpServer,
    private val executor: ExecutorService,
) {

    val address: InetSocketAddress
        get() = server.address

    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        private const val THREADS = 4

        fun start(address: InetSocketAddress, router: HarnessRouter, gson: Gson = GsonUtils.GSON): HarnessHttpServer {
            val threadCount = AtomicInteger()
            val executor = Executors.newFixedThreadPool(THREADS) { task ->
                Thread(task, "HarnessHttp-${threadCount.incrementAndGet()}").apply { isDaemon = true }
            }
            val server = HttpServer.create(address, 0)
            server.executor = executor
            server.createContext("/") { exchange -> respond(exchange, router, gson) }
            server.start()
            return HarnessHttpServer(server, executor)
        }

        private fun respond(exchange: HttpExchange, router: HarnessRouter, gson: Gson) {
            exchange.use {
                val request = HarnessRequest(
                    method = it.requestMethod,
                    path = it.requestURI.path,
                    query = parseQuery(it.requestURI.rawQuery),
                    body = it.requestBody.readAllBytes().decodeToString(),
                )
                val response = router.handle(request)
                val bytes = (gson.toJson(response.body) + "\n").toByteArray()
                it.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                it.sendResponseHeaders(response.status, bytes.size.toLong())
                it.responseBody.write(bytes)
            }
        }
    }
}
