package game.idle.flow

import game.idle.location.Tile

/**
 * A step's own work spot (S07e), kept under [KEY] as [Tile.text]: a tile picked on the world map, or none for From the
 * flow, where the flow last went.
 */
object StepSpot {

    const val KEY = "spot"

    const val FROM_THE_FLOW = "From the flow"

    /** The configure screen's Work spot, above Within on the right (S01): the x button puts it back to From the flow. */
    fun field(): StepField.MapTile = StepField.MapTile(KEY, "Work spot", FieldColumn.RIGHT, none = FROM_THE_FLOW)

    /** The tile [settings] picked, null for From the flow; throws [FlowError] when it is not a tile. */
    fun read(settings: StepSettings): Tile? = settings[KEY]?.let { TileText.read(it, "the work spot") }

    /** Where a step with [settings] works, [context] being what the steps before it set up: its own tile, else the flow's spot. */
    fun of(settings: StepSettings, context: FlowContext): WorkSpot = read(settings)?.let(WorkSpot::Own) ?: context.workSpot

    /** The end of a summary: " at 3101 3509" for a picked tile (Maxime, 2026-10-11), nothing for From the flow. */
    fun suffix(settings: StepSettings): String = settings[KEY]?.let { " at $it" }.orEmpty()
}
