package game.idle.autopilot.bank

import game.idle.autopilot.travel.TripPlanning
import game.idle.autopilot.travel.Traveller
import game.idle.location.Bank
import game.idle.movement.TravelWeb
import game.idle.movement.reachingTiles
import io.luna.game.model.Position
import io.luna.game.model.World

/**
 * [BankTrips] of the game world: a bank whose booth the web knows is walked to its stand, to which the web's walks are
 * already linked, so picking the nearest of many costs no searches; another is walked to the tiles its booth is used
 * from. Trips are [traveller]'s.
 */
class LunaBankTrips(private val world: World, private val traveller: Traveller, private val web: TravelWeb?) : BankTrips {

    override fun usableFrom(bank: Bank): List<Position> {
        val tile = bank.booth.toPosition()
        val booth = boothOn(world, tile) ?: return emptyList()
        return web?.bankStand(tile)?.let(::listOf) ?: reachingTiles(world.collisionManager, booth, from = tile)
    }

    /** Waits for the plan when it is made off the game thread: the bank is picked when the step starts. */
    override fun plan(ends: List<Position>): TripPlanning = traveller.plan(ends).join()
}
