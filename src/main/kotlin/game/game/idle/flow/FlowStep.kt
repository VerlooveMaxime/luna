package game.idle.flow

import game.idle.location.Location
import io.luna.game.model.mob.Player

/** A line of a flow the player could not have meant; the message is shown as is. */
class FlowError(override val message: String) : RuntimeException(message)

/**
 * One kind of flow step, everything about it in one place: how its line reads, which fields the flow builder shows
 * for it, and what it resolves to. The runner, parser, resolver and builder only know this interface, so a new kind
 * of step is a new implementation added to the grammar ([FlowGrammar]).
 */
interface StepType {

    /** The first word of the step's line. */
    val keyword: String

    /** What the builder's kind field shows. */
    val label: String

    /** The step's grammar, as the help text lists it. */
    val usage: String

    /** The builder's fields, in the order of [FlowStep.values]. */
    val fields: List<StepField>

    /** The field values of a line from the words after the keyword, lower-cased; throws [FlowError]. */
    fun parse(words: List<String>): List<String>

    /** The line [parse] reads back as [values]. */
    fun line(values: List<String>): String

    /** Checks [values] against the data and what the steps before it set up; throws [FlowError]. */
    fun resolve(values: List<String>, context: FlowContext): ResolvedStep
}

/** A field of a step in the builder; [choices] may depend on the values of the step's other fields. */
class StepField(val label: String, val choices: (values: List<String>) -> List<String>)

/** One line of a flow as typed, before what it names is checked against the data. */
data class FlowStep(val type: StepType, val values: List<String>) {

    fun line(): String = type.line(values)
}

/** What a step can rely on from the steps before it in the flow. */
data class FlowContext(val location: Location? = null, val gathered: Set<Int> = emptySet())

/** A step checked against the data: every name became the thing it names, so it can run. */
interface ResolvedStep {

    /** What the steps after this one can rely on. */
    fun after(context: FlowContext): FlowContext = context

    /** The activity that carries this step out for [player]. */
    fun activity(player: Player): StepActivity
}
