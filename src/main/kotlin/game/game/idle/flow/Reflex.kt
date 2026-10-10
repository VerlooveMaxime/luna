package game.idle.flow

/**
 * A reflex of a flow as the player set it up (S07c): "when X, do Y", kept beside the steps and saved with them, working
 * only while a step it is attached to runs. [id] stays put while the reflexes move, so steps attach it by id (0 until
 * the flow gives it one, [FlowIds]). Its settings by key, as text, like a step's: a missing one reads as its default
 * (keys in [ReflexKeys]), so the reflexes S13 adds need no conversion.
 */
data class ReflexSettings(val id: Int = 0, val values: Map<String, String> = emptyMap()) {

    operator fun get(key: String): String? = values[key]
}

/** The settings of a reflex and their values. */
object ReflexKeys {

    /** What it does: [EAT] (the default) or [RUN_AWAY]. */
    const val DO = "do"
    const val EAT = "eat"
    const val RUN_AWAY = "run"

    /** When it fires: hitpoints below this share of full, [DEFAULT_BELOW] without one. */
    const val BELOW = "below"
    const val DEFAULT_BELOW = 50
    val BELOW_RANGE = 1..99
    const val BELOW_RULE = "hitpoints below takes 1 to 99 percent"

    /** What an eat reflex eats ([StepItems]), any food when none. */
    const val FOODS = "foods"

    /** What follows running away: [STOP] (the default) or [JUMP] to the step whose id [STEP] holds. */
    const val THEN = "then"
    const val STOP = "stop"
    const val JUMP = "jump"
    const val STEP = "step"
}

/** A reflex checked against its flow: its [number] in the flow's list (from 1), when it fires and what it does. */
data class ResolvedReflex(val number: Int, val trigger: ReflexTrigger, val action: ReflexAction)

/** When a reflex fires, the player's When; S13 adds prayer points, a boost fallen, poison and being attacked. */
sealed interface ReflexTrigger {

    fun holds(body: ReflexBody): Boolean

    data class HitpointsBelow(val percent: Int) : ReflexTrigger {

        override fun holds(body: ReflexBody): Boolean = body.health().below(percent)
    }
}

/** What a reflex does, the player's Do. */
sealed interface ReflexAction {

    /** Eats the first of [foods] the bag holds, in bag order; any food when [foods] is empty. */
    data class Eat(val foods: Set<Int>) : ReflexAction

    /** Runs from what attacks the player, then [then]. */
    data class RunAway(val then: ReflexThen) : ReflexAction
}

/** What follows a reflex that runs away. */
sealed interface ReflexThen {

    data object StopFlow : ReflexThen

    /** The flow goes on from the step at [index], which counts no lap. */
    data class JumpTo(val index: Int) : ReflexThen
}

/** The player's hitpoints out of [full], their unboosted level. */
data class Health(val hitpoints: Int, val full: Int) {

    fun below(percent: Int): Boolean = hitpoints * 100 < full * percent

    /** As the chat box says it: "9/20". */
    fun text(): String = "$hitpoints/$full"
}
