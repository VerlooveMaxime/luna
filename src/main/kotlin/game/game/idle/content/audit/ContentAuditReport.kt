package game.idle.content.audit

import java.time.LocalDate

/** Plain-text audit files for a human: an overview, one file per zone, and one for the regions outside every zone. */
class ContentAuditReport(private val areas: List<AreaAudit>, private val date: LocalDate) {

    /** File name to content, the overview first. */
    fun files(): Map<String, String> {
        val (zones, unzoned) = areas.partition { it.zone }
        return mapOf(OVERVIEW to overview(zones, unzoned)) +
            zones.associate { "${it.name}.txt" to zoneFile(it) } +
            mapOf(UNZONED to unzonedFile(unzoned))
    }

    private fun overview(zones: List<AreaAudit>, unzoned: List<AreaAudit>): String {
        val header = listOf(
            "Content audit, $date",
            "Read from the running server: spawned npcs, placed objects with a menu option, their definitions, the",
            "combat definitions and drop tables loaded, and the click handlers plugins registered.",
            "Zones are the bot travel zones, one file each; every other region is in $UNZONED.",
            "",
            "Kinds lacking something, world-wide:",
            "  ${distinctKinds { it.withoutCombatDefinition }} attackable kinds without a combat definition " +
                "(Luna fights them with Man's stats)",
            "  ${distinctKinds { area -> area.placeholderCombat.map { it.shortfall } }} attackable kinds with a " +
                "placeholder combat definition",
            "  ${distinctKinds { it.withoutDropTable }} attackable kinds without a drop table",
            "  ${distinctOptions { it.unhandledNpcOptions }} npc options and " +
                "${distinctOptions { it.unhandledObjectOptions }} object options that reach no handler",
            "",
            "Per area, counted per kind: no combat, placeholder combat, no drops, npc and object options without a " +
                "handler.",
            "",
            ROW.format("area", "npcs", "no combat", "placeholder", "no drops", "npc opts", "objects", "object opts"),
        )
        val rows = zones.map { row(it.name, listOf(it)) } + row("unzoned (${unzoned.size} regions)", unzoned)
        return text(header + rows)
    }

    private fun row(name: String, areas: List<AreaAudit>): String =
        ROW.format(
            name,
            areas.sumOf { it.npcCount },
            areas.sumOf { it.withoutCombatDefinition.size },
            areas.sumOf { it.placeholderCombat.size },
            areas.sumOf { it.withoutDropTable.size },
            areas.sumOf { area -> area.unhandledNpcOptions.values.sumOf { it.size } },
            areas.sumOf { it.objectCount },
            areas.sumOf { area -> area.unhandledObjectOptions.values.sumOf { it.size } },
        )

    private fun distinctKinds(shortfalls: (AreaAudit) -> List<Shortfall>): Int =
        areas.flatMap(shortfalls).map { it.kind.id }.distinct().size

    private fun distinctOptions(options: (AreaAudit) -> Map<MenuOption, List<Shortfall>>): Int =
        areas.flatMap { area -> options(area).flatMap { (option, kinds) -> kinds.map { option to it.kind.id } } }
            .distinct()
            .size

    private fun zoneFile(area: AreaAudit): String =
        text(listOf("Content audit: ${area.name}, $date", "Regions: ${area.regions.joinToString()}") + section(area))

    private fun unzonedFile(unzoned: List<AreaAudit>): String {
        val listed = unzoned.filter { it.findingCount > 0 }
        val header = listOf(
            "Content audit: regions outside every zone, $date",
            "${unzoned.size} regions hold npcs or objects with a menu option; the ${listed.size} that lack something " +
                "are listed by id.",
        )
        return text(header + listed.flatMap { listOf("", "== ${regionTitle(it.regions.single())} ==") + section(it) })
    }

    private fun section(area: AreaAudit): List<String> =
        listOf(
            "Spawned npcs: ${area.npcCount} of ${area.npcKindCount} kinds. " +
                "Objects with a menu option: ${area.objectCount} of ${area.objectKindCount} kinds.",
            "",
            "Attackable npcs without a combat definition (Luna fights them with Man's stats):",
        ) + kindLines(area.withoutCombatDefinition) +
            PLACEHOLDER_HEADING + placeholderLines(area.placeholderCombat) +
            "Attackable npcs without a drop table:" + kindLines(area.withoutDropTable) +
            "Npc options that reach no handler:" + optionLines(area.unhandledNpcOptions) +
            "Object options that reach no handler (the server never receives options 4 and 5):" +
            optionLines(area.unhandledObjectOptions)

    private fun kindLines(shortfalls: List<Shortfall>): List<String> =
        shortfalls.map(::kindLine).ifEmpty { listOf(NONE) }

    private fun kindLine(shortfall: Shortfall): String =
        "  x${shortfall.count.toString().padEnd(4)} ${label(shortfall.kind)}"

    private fun placeholderLines(placeholders: List<PlaceholderCombat>): List<String> =
        placeholders.map { "${kindLine(it.shortfall)}: ${evidence(it.stats)}" }.ifEmpty { listOf(NONE) }

    private fun evidence(stats: CombatStats): String =
        if (stats.everySkillZero) {
            "every skill 0, max hit ${stats.maximumHit}"
        } else {
            "max hit ${stats.maximumHit}, strength ${stats.strength} gives ${stats.strengthMaxHit}"
        }

    private fun optionLines(options: Map<MenuOption, List<Shortfall>>): List<String> =
        options.map { (option, shortfalls) ->
            "  ${readable(option.label)} (option ${option.number}): " +
                shortfalls.joinToString { "${label(it.kind)} x${it.count}" }
        }.ifEmpty { listOf(NONE) }

    /** Luna's decoder reads binary data as the options of some varbit objects (farming patches). */
    private fun readable(label: String): String =
        if (label.all { it in ' '..'~' }) label else "unreadable label of ${label.length} characters"

    private fun label(kind: Kind): String =
        if (kind.combatLevel > 0) {
            "${kind.name} (${kind.id}, level ${kind.combatLevel})"
        } else {
            "${kind.name} (${kind.id})"
        }

    companion object {
        const val OVERVIEW = "overview.txt"
        const val UNZONED = "unzoned.txt"
        private const val NONE = "  none"
        private const val PLACEHOLDER_HEADING = "Attackable npcs with a placeholder combat definition " +
            "(max hit under half of what their strength gives, or every skill 0):"
        private const val ROW = "%-28s %6s %10s %12s %9s %9s %8s %12s"

        private const val REGION_SIZE = 64

        /** A region id packs the region's x in its high byte and its y in its low byte, both in 64-tile steps. */
        fun regionTitle(region: Int): String {
            val x = (region shr 8) * REGION_SIZE
            val y = (region and 0xFF) * REGION_SIZE
            return "region $region: x $x-${x + REGION_SIZE - 1}, y $y-${y + REGION_SIZE - 1}"
        }

        private fun text(lines: List<String>): String = lines.joinToString(separator = "\n", postfix = "\n")
    }
}
