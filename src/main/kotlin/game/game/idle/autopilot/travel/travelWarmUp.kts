package game.idle.autopilot.travel

import api.predef.*
import api.predef.ext.*
import game.idle.movement.TravelWebs
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Reads the travel web in the first tick, once the server has generated any missing web-walker data, so the first
 * trip does not hold the game thread for the two seconds it takes.
 */
on(ServerLaunchEvent::class) {
    world.scheduleOnce(1) { TravelWebs.SERVER.of(world) }
}
