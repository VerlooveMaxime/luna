package game.idle.autopilot.bank

/** A bank step's order that deposits the whole bag and withdraws nothing, skipping itself when stuck. */
val EVERYTHING = BankOrder(DepositRule.Everything, emptyList(), IfStuck.SKIP)
