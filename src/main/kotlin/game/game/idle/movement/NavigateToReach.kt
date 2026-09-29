package game.idle.movement

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
fun navigateToReach(player: Player, target: GameObject): CompletableFuture<NavigationResult> {
    val collision = player.world.collisionManager
    val tile = approachTiles(target.position, maxOf(target.sizeX(), target.sizeY()), from = player.position)
        .firstOrNull { !collision.isBlocked(it, false) && collision.reached(it, target, STANDARD_SIZE) }
    return when (tile) {
        null -> player.navigator.navigate(target, true, false)
        else -> player.navigator.navigate(tile, true)
    }
}
