package game.idle.ui

import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.StepType

/**
 * The step under construction in the flow builder: a kind of step and its settings. Settings are kept per kind of
 * step, so cycling through the kinds and back loses nothing. Fields are addressed by their place in the builder.
 */
data class FlowDraft(val type: StepType, private val settingsByType: Map<StepType, StepSettings> = emptyMap()) {

    /** The settings of [type]; a kind not touched yet starts at the default or first choice of each field, tiles blank. */
    val settings: StepSettings
        get() = settingsByType[type] ?: settled(type, StepSettings(type.kind))

    /** The value of field [index] as the builder shows it, "" when it has none. */
    fun value(index: Int): String = type.fields.getOrNull(index)?.let { settings[it.key] } ?: ""

    fun nextType(types: List<StepType>): FlowDraft = copy(type = types.after(type), settingsByType = settingsByType + (type to settings))

    /**
     * The next choice of field [index], wrapping around; a field whose value the change took off its choices moves to
     * its default or first choice. A kind of step without that choice field stays as it is.
     */
    fun nextValue(index: Int): FlowDraft {
        val field = type.fields.getOrNull(index) as? StepField.Choice ?: return this
        return withValue(index, field.choices(settings).after(value(index)))
    }

    /** Field [index] set to [value], the other fields settled around it. */
    fun withValue(index: Int, value: String): FlowDraft {
        val field = type.fields[index]
        return copy(settingsByType = settingsByType + (type to settled(type, settings.with(field.key, value))))
    }

    /** Every blank tile field of the current kind set to [tile]. */
    fun withBlankTiles(tile: String): FlowDraft {
        val filled = type.fields.filterIsInstance<StepField.MapTile>().fold(settings) { current, field ->
            if (current[field.key] == null) current.with(field.key, tile) else current
        }
        return copy(settingsByType = settingsByType + (type to filled))
    }

    /** This draft loaded with an existing step of [stepType], keeping what the other kinds of step had. */
    fun editing(stepType: StepType, step: StepSettings): FlowDraft =
        copy(type = stepType, settingsByType = settingsByType + (stepType to step))

    companion object {
        /** The draft a player starts from: the first kind of step. */
        fun first(types: List<StepType>): FlowDraft = FlowDraft(types.first())
    }
}

/**
 * [settings] with each choice that its field no longer offers replaced by the field's default or first choice (no
 * value when it offers none); tiles stay as they are. A field's choices may depend on the others, so this repeats once
 * per field, enough for any chain.
 */
private fun settled(type: StepType, settings: StepSettings): StepSettings {
    var current = settings
    repeat(type.fields.size) {
        val before = current
        current = type.fields.fold(before) { settling, field -> settling.with(field.key, settled(field, before)) }
    }
    return current
}

private fun settled(field: StepField, settings: StepSettings): String {
    val value = settings[field.key] ?: ""
    return when (field) {
        is StepField.Choice -> {
            val choices = field.choices(settings)
            if (value in choices) value else field.default?.takeIf { it in choices } ?: choices.firstOrNull() ?: ""
        }
        is StepField.MapTile -> value
    }
}

/** The element after [current], wrapping around; the first one when [current] is not in the list. */
private fun <T> List<T>.after(current: T): T = if (isEmpty()) current else this[(indexOf(current) + 1) % size]
