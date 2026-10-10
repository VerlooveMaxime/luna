package game.idle.flow

/**
 * Ids for a flow's steps and reflexes (S07c): each keeps its own while they move, so a step attaches a reflex and a
 * reflex jumps to a step by id. [NONE] is a step or reflex not given one yet.
 */
object FlowIds {

    const val NONE = 0

    /** [steps] with each that has no id given the next one after the highest, in order. */
    fun steps(steps: List<StepSettings>): List<StepSettings> = filled(steps, StepSettings::id) { step, id -> step.copy(id = id) }

    /** [reflexes] with each that has no id given the next one after the highest, in order. */
    fun reflexes(reflexes: List<ReflexSettings>): List<ReflexSettings> =
        filled(reflexes, ReflexSettings::id) { reflex, id -> reflex.copy(id = id) }

    private fun <T> filled(all: List<T>, idOf: (T) -> Int, withId: (T, Int) -> T): List<T> {
        var last = all.map(idOf).maxOrNull() ?: NONE
        return all.map { item -> if (idOf(item) == NONE) withId(item, ++last) else item }
    }
}
