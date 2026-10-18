package game.harness

import com.google.gson.JsonElement

// JSON shapes of the harness answers. Gson writes the properties in snake_case (GsonUtils.GSON naming policy).

data class WorldView(
    val tick: Long,
    val playersOnline: Int,
    val botsOnline: Int,
    val harnessPlayersOnline: Int,
    val uptimeSeconds: Long,
)

data class PositionView(val x: Int, val y: Int, val z: Int)

data class PlayerSummary(
    val name: String,
    val index: Int,
    val bot: Boolean,
    val harness: Boolean,
    val position: PositionView,
)

/** A menu option as the client numbers it: [option] is what the click endpoints take. */
data class OptionView(val option: Int, val name: String)

data class SkillView(val id: Int, val name: String, val level: Int, val staticLevel: Int, val xp: Double)

data class ItemView(val slot: Int, val id: Int, val name: String, val amount: Int, val options: List<OptionView>)

data class PlayerView(
    val name: String,
    val index: Int,
    val rights: String,
    val bot: Boolean,
    val harness: Boolean,
    val position: PositionView,
    val runEnergy: Double,
    val specialEnergy: Int,
    val running: Boolean,
    val walking: Boolean,
    val combatLevel: Int,
    val skills: List<SkillView>,
    val inventory: List<ItemView>,
    val equipment: List<ItemView>,
    val bankItems: Int,
    val actions: List<String>,
    val overlays: Map<String, String>,
    val attributes: Map<String, JsonElement>,
)

data class ObjectView(
    val id: Int,
    val name: String,
    val position: PositionView,
    val distance: Int,
    val type: String,
    val options: List<OptionView>,
)

data class NpcView(
    val index: Int,
    val id: Int,
    val name: String,
    val position: PositionView,
    val distance: Int,
    val combatLevel: Int,
    val options: List<OptionView>,
)

data class GroundItemView(
    val id: Int,
    val name: String,
    val amount: Int,
    val position: PositionView,
    val distance: Int,
    val options: List<OptionView>,
)

data class NearbyPlayerView(
    val name: String,
    val index: Int,
    val bot: Boolean,
    val position: PositionView,
    val distance: Int,
)

data class NearbyView(
    val center: PositionView,
    val radius: Int,
    val objects: List<ObjectView>,
    val npcs: List<NpcView>,
    val groundItems: List<GroundItemView>,
    val players: List<NearbyPlayerView>,
)

data class MessagesView(val next: Long, val messages: List<RecordedMessage>)

data class ActionView(val player: String, val action: String, val detail: String)

/** The content audit report, file name to text, as `.memory/artifacts/content-audit/` keeps it. */
data class ContentAuditView(val files: Map<String, String>)

/**
 * A step of a flow: its [id], kind and settings, the ids of the [reflexes] attached to it in its order, the [lines] the
 * builder's slot shows under its kind, its [problem], the red reason the slot shows when the step cannot work, null
 * when it can; and its run (S12): [goal] as the slot words it (null without one), whether it is [met] (the step skipped
 * for the run), what it [counted] over the run so far, and the withdrawals a bank step leaves out ([leftOut], item ids).
 */
data class FlowStepView(
    val id: Int,
    val kind: String,
    val values: Map<String, String>,
    val reflexes: List<Int>,
    val lines: List<String>,
    val problem: String?,
    val goal: String?,
    val met: Boolean,
    val counted: Int,
    val leftOut: List<Int>,
)

data class FlowReflexView(val id: Int, val values: Map<String, String>)

/** A flow's stop condition as its settings keep it (S12): its kind (never, laps, time, goals) and the number typed after it. */
data class FlowStopView(val kind: String, val after: String)

/** What a flow's trips may spend (web-walker movement Q1): coins for tolls and fares, teleport runes, jewellery charges. */
data class FlowTravelView(val coins: Boolean, val runes: Boolean, val charges: Boolean)

/**
 * A player's flow; [blocked] is why its running step cannot go on, as the overlay shows it, null while it can, and
 * [waiting] what it waits for the world to supply, null for nothing, and [underWay] what its trip does on its way
 * ("Opening the door"), the overlay's white line; [laps] counts the laps since Run, [stop] is when the flow stops
 * (S12) and [travel] what its trips may spend.
 */
data class FlowView(
    val player: String,
    val running: Boolean,
    val stepIndex: Int,
    val laps: Int,
    val blocked: String?,
    val waiting: String?,
    val underWay: String?,
    val stop: FlowStopView,
    val travel: FlowTravelView,
    val steps: List<FlowStepView>,
    val reflexes: List<FlowReflexView>,
)
