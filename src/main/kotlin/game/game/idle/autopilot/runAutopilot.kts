package game.idle.autopilot

import api.predef.*
import game.idle.flow.FlowCommand
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.FlowRunner
import game.idle.location.LocationCatalog
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Resolved at boot so a typo in the data file stops the server instead of surfacing at the first `::idle`.
val resolver = FlowResolver(LocationCatalog.load(LocationCatalog.PATH))
logger.info("Loaded {} idle locations.", resolver.size)

lateinit var autopilot: Autopilot<LunaAutopilotPlayer>

autopilot = Autopilot(WorldTickScheduler(world)) { autopilotPlayer ->
    val state = autopilotPlayer.idleState
    val steps = try {
        resolver.resolve(state.flow)
    } catch (e: FlowError) {
        null
    }
    steps?.let {
        val runner = FlowRunner(it, state.stepIndex, autopilotPlayer, autopilotPlayer) { autopilot.stop(autopilotPlayer) }
        AutopilotDriver(runner, config.decisionDelayTicks)
    }
}

val flowCommand = FlowCommand(autopilot, resolver)

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then { autopilot.onLogin(LunaAutopilotPlayer(plr)) }

on(LogoutEvent::class)
    .filter { !plr.isBot }
    .then { autopilot.onLogout(LunaAutopilotPlayer(plr)) }

cmd("idle") {
    flowCommand.idle(LunaAutopilotPlayer(plr), args.toList(), plr.position)
}

cmd("flow") {
    flowCommand.flow(LunaAutopilotPlayer(plr), args.toList())
}
