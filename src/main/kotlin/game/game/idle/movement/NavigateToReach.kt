package game.idle.movement

import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionManager
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import java.util.concurrent.CompletableFuture

/**
 * Walks [player] to the nearest tile from which [target] can be used. Navigating to the object itself stops on the
 * closest free tile, which can be a diagonal one that the reach check refuses (bots are exempt from that check,
 * players are not). The client instead paths to a reaching tile, so do the same.
 */
fun navigateToReach(player: Player, target: GameObject): CompletableFuture<NavigationResult> =
    when (val tile = reachingTiles(player.world.collisionManager, target, from = player.position).firstOrNull()) {
        null -> player.navigator.navigate(target, true, false)
        else -> player.navigator.navigate(tile, true)
    }

/** The tiles around [target] a player can stand on and use it from, nearest to [from] first. */
fun reachingTiles(collision: CollisionManager, target: GameObject, from: Position): List<Position> {
    val footprint = footprintOf(target)
    return approachTiles(footprint.position, footprint.width, footprint.height, from)
        .filter { canStandOn(collision, it) && collision.reached(it, target, STANDARD_SIZE) }
}
