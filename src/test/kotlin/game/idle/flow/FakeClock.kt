package game.idle.flow

/** A clock a test moves by hand, in milliseconds. */
class FakeClock(var now: Long = 0) : Clock {

    override fun now(): Long = now

    fun advanceMinutes(minutes: Int) {
        now += minutes * 60_000L
    }
}
