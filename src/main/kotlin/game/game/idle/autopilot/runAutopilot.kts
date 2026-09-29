package game.idle.autopilot

import api.predef.*
import game.idle.autopilot.woodcutting.LunaWoodcutter
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

val autopilot = Autopilot<LunaAutopilotPlayer>(WorldTickScheduler(world)) {
    AutopilotDriver(WoodcuttingActivity(LunaWoodcutter(it.player, config.treeSearchRadius)), config.decisionDelayTicks)
}

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then { autopilot.onLogin(LunaAutopilotPlayer(plr)) }

on(LogoutEvent::class)
    .filter { !plr.isBot }
    .then { autopilot.onLogout(LunaAutopilotPlayer(plr)) }

cmd("idle") {
    autopilot.toggle(LunaAutopilotPlayer(plr))
}
