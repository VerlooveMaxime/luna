package game.idle.autopilot

import api.predef.*
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.FlowRunner
import game.idle.idleState
import game.idle.flow.option.LunaGameNames
import game.idle.ui.BuilderOverview
import game.idle.ui.BuilderScreen
import game.idle.ui.BuilderWidgets
import game.idle.ui.BuilderWindow
import game.idle.ui.ClientFont
import game.idle.ui.FlowBuilder
import game.idle.ui.FlowWidgets
import game.idle.ui.IdleUi
import game.idle.ui.LunaBuilderUi
import game.idle.ui.LunaFlowUi
import game.idle.ui.MapPickEvent
import io.luna.game.event.impl.ArrangeItemEvent
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Loaded at boot so a typo in a data file stops the server instead of surfacing at the first step that uses it.
val steps = IdleSteps.load()
val resolver = FlowResolver(steps.types)
val builderWindow = BuilderWindow(BuilderOverview(resolver, LunaGameNames, ClientFont.fromCache(ctx.cache)), config.stepSlots)
val ui = IdleUi(steps.types::summary, builderWindow)
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
val builderUi = LunaBuilderUi(BuilderScreen(autopilot, resolver, config.stepSlots), builderWindow, ui)

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

// The new builder (flow builder v2), for developers until S06b gives it the Idle tab's button.
cmd("builder", RIGHTS_DEV) { builderWindow.open(plr) }

on(ButtonClickEvent::class)
    .filter { BuilderWidgets.owns(id) }
    .then { builderUi.click(plr, id) }

on(ArrangeItemEvent::class)
    .filter { widgetId == BuilderWidgets.SLOTS }
    .then { builderUi.arrange(plr, fromIndex, toIndex) }

on(MapPickEvent::class) {
    flowUi.picked(plr, tile)
}
