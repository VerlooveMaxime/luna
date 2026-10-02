package game.idle.autopilot

import api.predef.*
import game.idle.flow.FlowCommand
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.FlowRunner
import game.idle.idleState
import game.idle.location.LocationCatalog
import game.idle.ui.FlowBuilder
import game.idle.ui.FlowWidgets
import game.idle.ui.IdleUi
import game.idle.ui.LunaFlowUi
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Resolved at boot so a typo in the data file stops the server instead of surfacing at the first `::idle`.
val resolver = FlowResolver(LocationCatalog.load(LocationCatalog.PATH))
logger.info("Loaded {} idle locations.", resolver.size)

val autopilot = Autopilot<LunaAutopilotPlayer>(WorldTickScheduler(world)) { autopilotPlayer ->
    val state = autopilotPlayer.idleState
    val steps = try {
        resolver.resolve(state.flow)
    } catch (e: FlowError) {
        null
    }
    steps?.let { AutopilotDriver(FlowRunner(it, state.stepIndex, autopilotPlayer, autopilotPlayer), config.decisionDelayTicks) }
}

val flowCommand = FlowCommand(autopilot, resolver)
val flowUi = LunaFlowUi(FlowBuilder(autopilot, resolver))

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then {
        IdleUi.installTab(plr, plr.idleState)
        autopilot.onLogin(LunaAutopilotPlayer(plr))
    }

on(LogoutEvent::class)
    .filter { !plr.isBot }
    .then {
        autopilot.onLogout(LunaAutopilotPlayer(plr))
        flowUi.forget(plr)
    }

on(ButtonClickEvent::class)
    .filter { FlowWidgets.owns(id) }
    .then { flowUi.click(plr, id) }

cmd("idle") {
    flowCommand.idle(LunaAutopilotPlayer(plr), args.toList(), plr.position)
}

cmd("flow") {
    flowCommand.flow(LunaAutopilotPlayer(plr), args.toList())
}
