package game.idle.autopilot.travel

import api.predef.*
import api.predef.ext.*
import game.idle.movement.TravelWebs
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Reads the travel web in the first tick, once the server has generated any missing web-walker data, so the first
 * trip does not hold the game thread for the two seconds it takes; by then Luna's launch scripts have removed their
 * doors, which the log names.
 */
on(ServerLaunchEvent::class) {
    world.scheduleOnce(1) {
        val removed = TravelWebs.SERVER.of(world)?.removedDoors.orEmpty().map { it.position }
        logger.info("The travel web walks through {} doors Luna removed: {}.", removed.size,
                    removed.joinToString { "${it.x} ${it.y} ${it.z}" })
    }
}
