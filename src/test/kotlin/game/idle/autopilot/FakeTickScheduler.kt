package game.idle.autopilot

class FakeTickScheduler : TickScheduler {

    private val active = mutableListOf<() -> Unit>()

    val activeCount: Int
        get() = active.size

    override fun everyTick(action: () -> Unit): ScheduledTick {
        active += action
        return ScheduledTick { active -= action }
    }

    fun tick() {
        active.toList().forEach { it() }
    }
}
