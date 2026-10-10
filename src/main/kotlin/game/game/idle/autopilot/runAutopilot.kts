package game.idle.autopilot

import api.predef.*
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.FlowRunner
import game.idle.flow.ReflexResolver
import game.idle.flow.ResolvedFlow
import game.idle.flow.SavedFlows
import game.idle.idleState
import game.idle.flow.option.LunaGameNames
import game.idle.ui.BuilderConfigure
import game.idle.ui.BuilderOverview
import game.idle.ui.BuilderScreen
import game.idle.ui.BuilderWidgets
import game.idle.ui.BuilderWindow
import game.idle.ui.ClientFont
import game.idle.ui.FlowWidgets
import game.idle.ui.IdleTab
import game.idle.ui.IdleUi
import game.idle.ui.LunaBuilderUi
import game.idle.ui.MapPickEvent
import game.idle.autopilot.reflex.FoodOptions
import game.idle.flow.ReflexForm
import io.luna.game.event.impl.ArrangeItemEvent
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Loaded at boot so a typo in a data file stops the server instead of surfacing at the first step that uses it.
val steps = IdleSteps.load()
val resolver = FlowResolver(steps.types)
val reflexResolver = ReflexResolver(FoodOptions.portions())
val reflexForm = ReflexForm(FoodOptions(LunaGameNames), FoodOptions.portions(), steps.types, LunaGameNames)
val font = ClientFont.fromCache(ctx.cache)
val savedFlows = SavedFlows(config.savedFlowSlots)
val builderWindow = BuilderWindow(
    BuilderOverview(resolver, reflexResolver, reflexForm, LunaGameNames, font, savedFlows),
    BuilderConfigure(resolver, reflexResolver, reflexForm, LunaGameNames, font),
    config.stepSlots,
    config.reflexSlots,
)
val ui = IdleUi(steps.types::summary, IdleTab.fromCache(ctx.cache, config.savedFlowSlots), builderWindow)
logger.info("Loaded {} kinds of idle step.", steps.types.all.size)

val autopilot = Autopilot<LunaAutopilotPlayer>(WorldTickScheduler(world)) { autopilotPlayer ->
    val state = autopilotPlayer.idleState
    val resolved = try {
        ResolvedFlow(resolver.resolve(state.steps), reflexResolver.resolve(state.steps, state.reflexes))
    } catch (e: FlowError) {
        null
    }
    resolved?.let { AutopilotDriver(FlowRunner(it, state.stepIndex, autopilotPlayer), config.decisionDelayTicks) }
}

val builderUi = LunaBuilderUi(BuilderScreen(autopilot, resolver, reflexResolver, reflexForm, LunaGameNames, config.stepSlots, config.reflexSlots, savedFlows), builderWindow, ui, font)

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
    }

on(ButtonClickEvent::class)
    .filter { FlowWidgets.owns(id) }
    .then { builderUi.tab(plr, id) }

on(ButtonClickEvent::class)
    .filter { BuilderWidgets.owns(id) }
    .then { builderUi.click(plr, id) }

on(ArrangeItemEvent::class)
    .filter { widgetId == BuilderWidgets.SLOTS }
    .then { builderUi.arrange(plr, fromIndex, toIndex) }

on(ArrangeItemEvent::class)
    .filter { widgetId == BuilderWidgets.REFLEX_ROWS }
    .then { builderUi.arrangeReflexes(plr, fromIndex, toIndex) }

on(MapPickEvent::class) {
    builderUi.picked(plr, tile)
}
