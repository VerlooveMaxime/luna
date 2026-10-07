package game.idle.autopilot.bank

import game.idle.location.Bank
import io.luna.game.model.Direction
import io.luna.game.model.Position

/** Open ground but for [walls], tiles nothing steps onto; each bank is used from the tiles [usable] lists. */
class FakeBankTerrain(private val usable: Map<Bank, List<Position>>, private val walls: (Position) -> Boolean = { false }) :
    BankTerrain {

    var stepsAsked = 0
        private set

    override fun canStep(from: Position, direction: Direction): Boolean {
        stepsAsked++
        return !walls(from.translate(1, direction))
    }

    override fun usableFrom(bank: Bank): List<Position> = usable[bank].orEmpty()
}
