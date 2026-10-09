package game.idle.autopilot.mining

import io.luna.game.model.Position

/** Records each step as readable text, for example `walk to 3170,3454`. */
class FakeMiner(var view: MiningView) : Miner {

    var busy = false
    var ores = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): MiningView = view

    override fun mine(rock: RockCandidate) {
        steps += "mine ${rock.position.x},${rock.position.y}"
    }

    override fun walkTo(rock: RockCandidate) {
        steps += "walk to ${rock.position.x},${rock.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun ores(): Int = ores

    override fun stop() {
        steps += "stop"
    }
}

fun rock(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
    RockCandidate(2090, Position(x, y), distance, usableFromHere, Position(x - 1, y))

fun miningView(
    rocks: List<RockCandidate>,
    miningLevel: Int = 1,
    hasUsablePickaxe: Boolean = true,
    inventoryFull: Boolean = false,
    atLocation: Boolean = true,
) = MiningView(miningLevel, hasUsablePickaxe, inventoryFull, atLocation, rocks)
