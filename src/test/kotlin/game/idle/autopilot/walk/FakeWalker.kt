package game.idle.autopilot.walk

import game.idle.location.Tile

/** Records walks and messages; [here] is where the player stands. */
class FakeWalker(var here: Tile) : Walker {

    var busy = false
    var walks = 0
    val told = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun position(): Tile = here

    override fun walk() {
        walks++
    }

    override fun tell(message: String) {
        told += message
    }
}
