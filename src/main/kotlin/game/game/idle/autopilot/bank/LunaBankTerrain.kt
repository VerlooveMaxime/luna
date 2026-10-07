package game.idle.autopilot.bank

import game.idle.location.Bank
import game.idle.movement.reachingTiles
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.World

/** [BankTerrain] of the game world: its collision and the booths standing in it. */
class LunaBankTerrain(private val world: World) : BankTerrain {

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    override fun usableFrom(bank: Bank): List<Position> {
        val tile = bank.booth.toPosition()
        val booth = boothOn(world, tile) ?: return emptyList()
        return reachingTiles(world.collisionManager, booth, from = tile)
    }
}
