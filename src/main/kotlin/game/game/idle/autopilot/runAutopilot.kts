package game.idle.autopilot

import api.predef.*
import game.idle.autopilot.woodcutting.LunaWoodcutter
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import game.idle.location.LocationCatalog
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent

val config = AutopilotConfig.load(AutopilotConfig.PATH)

// Resolved at boot so a typo in the data file stops the server instead of surfacing at the first `::idle`.
val jobs = Jobs(LocationCatalog.load(LocationCatalog.PATH))
logger.info("Loaded {} idle locations.", jobs.size)

val autopilot = Autopilot<LunaAutopilotPlayer>(WorldTickScheduler(world)) { autopilotPlayer, job ->
    jobs.resolve(job)?.let { chop ->
        val activity = WoodcuttingActivity(LunaWoodcutter(autopilotPlayer.player, chop.spot), chop.action)
        AutopilotDriver(activity, config.decisionDelayTicks)
    }
}

val idleCommand = IdleCommand(autopilot, jobs)

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then { autopilot.onLogin(LunaAutopilotPlayer(plr)) }

on(LogoutEvent::class)
    .filter { !plr.isBot }
    .then { autopilot.onLogout(LunaAutopilotPlayer(plr)) }

cmd("idle") {
    idleCommand.run(LunaAutopilotPlayer(plr), args.toList(), plr.position)
}
