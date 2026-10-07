package game.harness

import api.predef.*
import game.idle.autopilot.AutopilotConfig
import game.idle.autopilot.IdleSteps
import game.idle.flow.FlowCheck
import game.idle.flow.FlowResolver
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerShutdownEvent

val harness = HarnessService { config ->
    LunaHarnessApi(
        world = world,
        gameThread = LunaGameThread(gameService, config.requestTimeout),
        headless = HeadlessPlayers(ctx, config),
        flows = FlowCheck(FlowResolver(IdleSteps.load().types), AutopilotConfig.load(AutopilotConfig.PATH).stepSlots),
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
