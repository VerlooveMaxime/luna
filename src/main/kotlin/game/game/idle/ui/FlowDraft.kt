package game.idle.ui

import game.idle.flow.FlowStep

enum class StepKind(val label: String) {
    CHOP("chop"),
    DROP("drop"),
    BANK("bank deposit all"),
}

/**
 * The step under construction in the flow builder. [line] is the text `FlowParser` will parse, so the builder can
 * only produce lines the command DSL accepts and the parser stays the one validator. [resource] is the kind of tree
 * for a chop step; later skills will put their rocks and fish there.
 */
data class FlowDraft(
    val kind: StepKind = StepKind.CHOP,
    val resource: String = "",
    val locationId: String = "",
) {

    fun line(): String =
        when (kind) {
            StepKind.CHOP -> "chop $resource @$locationId"
            StepKind.DROP -> "drop"
            StepKind.BANK -> "bank deposit all"
        }

    fun nextKind(): FlowDraft = copy(kind = StepKind.entries.after(kind))

    fun nextResource(resources: List<String>): FlowDraft = copy(resource = resources.after(resource))

    /** Moving to another location picks its first resource, since the old one may not be there. */
    fun withLocation(locationId: String, resources: List<String>): FlowDraft =
        copy(locationId = locationId, resource = resources.firstOrNull() ?: "")

    fun nextLocation(locationIds: List<String>, resourcesAt: (String) -> List<String>): FlowDraft {
        val next = locationIds.after(locationId)
        return withLocation(next, resourcesAt(next))
    }

    /** This draft loaded with an existing step, keeping the fields the step does not have. */
    fun editing(step: FlowStep): FlowDraft =
        when (step) {
            is FlowStep.Chop -> copy(kind = StepKind.CHOP, resource = step.tree, locationId = step.locationId)
            FlowStep.Drop -> copy(kind = StepKind.DROP)
            FlowStep.BankDepositAll -> copy(kind = StepKind.BANK)
        }

    companion object {
        /** The draft a player starts from: a chop at the first location. */
        fun first(locationIds: List<String>, resourcesAt: (String) -> List<String>): FlowDraft {
            val location = locationIds.firstOrNull() ?: ""
            return FlowDraft().withLocation(location, resourcesAt(location))
        }
    }
}

/** The element after [current], wrapping around; the first one when [current] is not in the list. */
private fun <T> List<T>.after(current: T): T = if (isEmpty()) current else this[(indexOf(current) + 1) % size]
