package game.idle.autopilot

import api.predef.*
import game.idle.location.LocationCatalog
import game.idle.autopilot.woodcutting.LunaWoodcutter
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import game.idle.autopilot.woodcutting.WoodcuttingSpot
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Resolved at boot so a typo in the data file stops the server instead of surfacing at the first flow step.
val locations = LocationCatalog.load(LocationCatalog.PATH)
locations.locations.forEach { WoodcuttingSpot.from(it) }
logger.info("Loaded {} idle locations.", locations.size)

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
