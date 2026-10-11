package game.idle.flow

/**
 * Counts what a [meter] reads, in the direction it counts only, so a count never falls: a rise and a fall inside one
 * decision do not cancel, and input the player gave late is counted when it goes. It reads the meter when [observe]d,
 * which the turn does on every poll, so a change during a long Luna action is seen.
 */
class Tally(private val meter: Meter) {

    private var last = read()
    private var total = 0

    /** Reads the meter and adds what moved in the counted direction since the last reading. */
    fun observe() {
        val now = read()
        total += counted(now - last)
        last = now
    }

    fun total(): Int = total

    private fun read(): Int =
        when (meter) {
            Meter.None -> 0
            is Meter.Rises -> meter.reading()
            is Meter.Falls -> meter.reading()
        }

    private fun counted(delta: Int): Int =
        when (meter) {
            Meter.None -> 0
            is Meter.Rises -> maxOf(0, delta)
            is Meter.Falls -> maxOf(0, -delta)
        }
}
