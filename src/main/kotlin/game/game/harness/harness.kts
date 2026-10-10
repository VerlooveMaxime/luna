package game.harness

import api.predef.*
import game.idle.autopilot.AutopilotConfig
import game.idle.autopilot.IdleSteps
import game.idle.flow.FlowCheck
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexResolver
import game.idle.autopilot.reflex.FoodOptions
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerShutdownEvent

val autopilotConfig = AutopilotConfig.load(AutopilotConfig.PATH)

val harness = HarnessService { config ->
    LunaHarnessApi(
        world = world,
        gameThread = LunaGameThread(gameService, config.requestTimeout),
        headless = HeadlessPlayers(ctx, config),
        flows = FlowCheck(
            FlowResolver(IdleSteps.load().types),
            ReflexResolver(FoodOptions.portions()),
            autopilotConfig.stepSlots,
            autopilotConfig.reflexSlots,
        ),
    )
}

on(ServerLaunchEvent::class) {
    harness.start(HarnessConfig.load(HarnessConfig.PATH, System.getenv()))
}

on(LoginEvent::class) {
    harness.onLogin(plr.client.channel) { world.currentTick }
}

on(ServerShutdownEvent::class) {
    harness.stop()
}
