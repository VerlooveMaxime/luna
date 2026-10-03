package game.idle.ui

import game.idle.flow.FlowStep
import game.idle.flow.StepType

/**
 * The step under construction in the flow builder: a kind of step and the values of its fields. [line] is the text
 * the grammar parses, so the builder can only produce lines the command DSL accepts and the grammar stays the one
 * validator. Values are kept per kind of step, so cycling through the kinds and back loses nothing.
 */
data class FlowDraft(val type: StepType, private val valuesByType: Map<StepType, List<String>> = emptyMap()) {

    /** The field values of [type]; a kind not touched yet starts at the first choice of each field. */
    val values: List<String>
        get() = valuesByType[type] ?: settled(type, List(type.fields.size) { "" })

    fun line(): String = type.line(values)

    fun nextType(types: List<StepType>): FlowDraft = copy(type = types.after(type), valuesByType = valuesByType + (type to values))

    /**
     * The next choice of field [index], wrapping around; a field whose value the change took off its choices moves to
     * its first choice. A kind of step without that field stays as it is.
     */
    fun nextValue(index: Int): FlowDraft {
        val field = type.fields.getOrNull(index) ?: return this
        val current = values
        val changed = current.toMutableList().also { it[index] = field.choices(current).after(current[index]) }
        return copy(valuesByType = valuesByType + (type to settled(type, changed)))
    }

    /** This draft loaded with an existing step, keeping what the other kinds of step had. */
    fun editing(step: FlowStep): FlowDraft = copy(type = step.type, valuesByType = valuesByType + (step.type to step.values))

    companion object {
        /** The draft a player starts from: the first kind of step. */
        fun first(types: List<StepType>): FlowDraft = FlowDraft(types.first())
    }
}

/**
 * [values] with each value that its field no longer offers replaced by the field's first choice ("" when it offers
 * none). A field's choices may depend on the others, so this repeats once per field, enough for any chain.
 */
private fun settled(type: StepType, values: List<String>): List<String> {
    var current = values
    repeat(type.fields.size) {
        val before = current
        current = type.fields.mapIndexed { index, field ->
            val choices = field.choices(before)
            if (before[index] in choices) before[index] else choices.firstOrNull() ?: ""
        }
    }
    return current
}

/** The element after [current], wrapping around; the first one when [current] is not in the list. */
private fun <T> List<T>.after(current: T): T = if (isEmpty()) current else this[(indexOf(current) + 1) % size]
