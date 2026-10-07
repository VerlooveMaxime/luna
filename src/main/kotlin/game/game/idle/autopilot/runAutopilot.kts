package game.idle.autopilot

import api.predef.*
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.FlowRunner
import game.idle.idleState
import game.idle.ui.FlowBuilder
import game.idle.ui.FlowWidgets
import game.idle.ui.IdleUi
import game.idle.ui.LunaFlowUi
import game.idle.ui.MapPickEvent
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Loaded at boot so a typo in a data file stops the server instead of surfacing at the first step that uses it.
val steps = IdleSteps.load()
val resolver = FlowResolver(steps.types)
val ui = IdleUi(steps.types::summary)
logger.info("Loaded {} kinds of idle step.", steps.types.all.size)

val autopilot = Autopilot<LunaAutopilotPlayer>(WorldTickScheduler(world)) { autopilotPlayer ->
    val state = autopilotPlayer.idleState
    val resolved = try {
        resolver.resolve(state.steps)
    } catch (e: FlowError) {
        null
    }
    resolved?.let { AutopilotDriver(FlowRunner(it, state.stepIndex, autopilotPlayer), config.decisionDelayTicks) }
}

val flowUi = LunaFlowUi(FlowBuilder(autopilot, resolver, config.stepSlots), ui)

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then {
        ui.installTab(plr, plr.idleState)
        autopilot.onLogin(LunaAutopilotPlayer(plr, ui))
    }

on(LogoutEvent::class)
    .filter { !plr.isBot }
    .then {
        autopilot.onLogout(LunaAutopilotPlayer(plr, ui))
        flowUi.forget(plr)
    }

on(ButtonClickEvent::class)
    .filter { FlowWidgets.owns(id) }
    .then { flowUi.click(plr, id) }

on(MapPickEvent::class) {
    flowUi.picked(plr, tile)
}
