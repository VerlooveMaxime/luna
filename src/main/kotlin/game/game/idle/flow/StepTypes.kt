package game.idle.flow

/** Every kind of step a flow can use, in the order the flow builder cycles through them, found by [StepType.kind]. */
class StepTypes(val all: List<StepType>) {

    init {
        require(all.isNotEmpty()) { "A flow needs at least one kind of step" }
        require(all.map { it.kind }.toSet().size == all.size) { "Two kinds of step share a name" }
    }

    private val byKind = all.associateBy { it.kind }

    fun find(kind: String): StepType? = byKind[kind]

    /** The step in a few words; a step of a kind no type knows shows its kind alone. */
    fun summary(step: StepSettings): String = find(step.kind)?.summary(step) ?: step.kind
}
