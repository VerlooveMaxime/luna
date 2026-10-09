package game.idle.autopilot.walk

import game.idle.location.Tile

/** Records walks; [here] is where the player stands. */
class FakeWalker(var here: Tile) : Walker {

    var busy = false
    var walks = 0

    override fun isBusy(): Boolean = busy

    override fun position(): Tile = here

    override fun walk() {
        walks++
    }
}
