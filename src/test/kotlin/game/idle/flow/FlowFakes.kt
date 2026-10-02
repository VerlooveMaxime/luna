package game.idle.flow

class FakeFlowPlayer : FlowPlayer {

    val savedSteps = mutableListOf<Int>()

    override fun saveStep(index: Int) {
        savedSteps += index
    }
}

/** Records acts as `<name>:<n>`; [done] ends the step. */
class FakeStepActivity(private val name: String, val log: MutableList<String>) : StepActivity {

    var busy = false
    var done = false
    private var acts = 0

    override fun isBusy(): Boolean = busy

    override fun isDone(): Boolean = done

    override fun act() {
        acts++
        log += "$name:$acts"
    }
}

class FakeStepActivities : StepActivities {

    val log = mutableListOf<String>()
    val started = mutableListOf<FakeStepActivity>()

    override fun chop(step: ResolvedStep.Chop): StepActivity = start("chop ${step.spot.location.id}")

    override fun drop(step: ResolvedStep.Drop): StepActivity = start("drop ${step.itemIds.size}")

    override fun bank(step: ResolvedStep.Bank): StepActivity = start("bank ${step.location.id}")

    private fun start(name: String): FakeStepActivity = FakeStepActivity(name, log).also { started += it }
}
