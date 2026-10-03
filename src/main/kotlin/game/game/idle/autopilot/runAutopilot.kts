package game.idle.autopilot

import api.predef.*
import game.idle.flow.FlowCommand
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.FlowRunner
import game.idle.idleState
import game.idle.location.BankCatalog
import game.idle.ui.FlowBuilder
import game.idle.ui.FlowWidgets
import game.idle.ui.IdleUi
import game.idle.ui.LunaFlowUi
import game.idle.ui.MapPickEvent
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Loaded at boot so a typo in the data file stops the server instead of surfacing at the first bank step.
val banks = BankCatalog.load(BankCatalog.PATH)
val resolver = FlowResolver(IdleSteps(banks).grammar)
logger.info("Loaded {} idle banks.", banks.banks.size)

val autopilot = Autopilot<LunaAutopilotPlayer>(WorldTickScheduler(world)) { autopilotPlayer ->
    val state = autopilotPlayer.idleState
    val resolved = try {
        resolver.resolve(state.flow)
    } catch (e: FlowError) {
        null
    }
    resolved?.let { AutopilotDriver(FlowRunner(it, state.stepIndex, autopilotPlayer), config.decisionDelayTicks) }
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

on(MapPickEvent::class) {
    flowUi.picked(plr, tile)
}

cmd("idle") {
    flowCommand.idle(LunaAutopilotPlayer(plr), args.toList())
}

cmd("flow") {
    flowCommand.flow(LunaAutopilotPlayer(plr), args.toList())
}
