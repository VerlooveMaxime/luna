package game.harness

import org.apache.logging.log4j.LogManager
import java.net.InetSocketAddress

/** Starts the HTTP server when the config enables it and stops it on shutdown. [createApi] wires the game side. */
class HarnessService(private val createApi: (HarnessConfig) -> HarnessApi) {

    private var server: HarnessHttpServer? = null

    /** The bound address, or `null` when the config leaves the harness off. */
    fun start(config: HarnessConfig): InetSocketAddress? {
        check(server == null) { "the harness is already running" }
        if (!config.enabled) {
            logger.info(
                "Agent harness is off; enable it in {} or with {}=true.",
                HarnessConfig.PATH,
                HarnessConfig.ENABLED_VARIABLE,
            )
            return null
        }
        val router = HarnessRouter(harnessRoutes(createApi(config)))
        val started = HarnessHttpServer.start(InetSocketAddress(config.bindAddress, config.port), router)
        server = started
        logger.info("Agent harness listening on http://{}:{}/", started.address.hostString, started.address.port)
        return started.address
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private companion object {
        val logger = LogManager.getLogger(HarnessService::class.java)
    }
}
