package game.idle.autopilot.mining

import game.idle.autopilot.Candidate
import game.idle.location.Tile
import io.luna.game.model.Position

/** Records each step as readable text, for example `walk to 3170,3454`; [anchor] is the step's area anchor. */
class FakeMiner(var view: MiningView, private val anchor: Tile = Tile(3200, 3200)) : Miner {

    var busy = false
    var ores = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): MiningView = view

    override fun mine(rock: Candidate) {
        steps += "mine ${rock.position.x},${rock.position.y}"
    }

    override fun walkTo(rock: Candidate) {
        steps += "walk to ${rock.position.x},${rock.position.y}"
    }

    override fun anchor(): Tile = anchor

    override fun ores(): Int = ores

    override fun interrupt() {
        steps += "interrupt"
    }
}

fun rock(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
    Candidate(2090, Position(x, y), distance, usableFromHere, Position(x - 1, y))

fun miningView(rocks: List<Candidate>, atLocation: Boolean = true) = MiningView(atLocation, rocks)
