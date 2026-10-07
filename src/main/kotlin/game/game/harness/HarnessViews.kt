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

data class FlowStepView(val kind: String, val values: Map<String, String>, val summary: String)

data class FlowView(val player: String, val running: Boolean, val stepIndex: Int, val steps: List<FlowStepView>)
