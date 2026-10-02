package game.idle.content.combat

import java.time.LocalDate

/** The plain-text account of one import, for review before the data file is committed. */
class CombatImportReport(private val areas: List<String>, private val date: LocalDate) {

    fun format(outcomes: List<ImportOutcome>): String {
        val updated = outcomes.filterIsInstance<ImportOutcome.Updated>()
        val changed = updated.filter { it.changes.isNotEmpty() }
        val noSource = outcomes.filterIsInstance<ImportOutcome.NoSource>()
        val noRow = outcomes.filterIsInstance<ImportOutcome.NoRow>()
        val bySource = CombatSource.entries.map { source -> "${source.label} ${changed.count { it.source == source }}" }
        val header = listOf(
            "Npc combat import, $date",
            "Areas: ${areas.joinToString()}",
            "Sources, in order: LostCity 377-wip by id; LostCity 289 by debugname when the name matches; " +
                "OSRS wiki (osrsreboxed-db) by name and combat level, every value to review.",
            "",
            "Attackable npc kinds spawned there: ${outcomes.size}. " +
                "Changed: ${changed.size} (${bySource.joinToString()}); " +
                "already matching: ${updated.size - changed.size}; no source: ${noSource.size}; " +
                "no row in npc_combat.jsonc: ${noRow.size}.",
        )
        val changes = changed.flatMap(::changeLines)
        val missing = listOf("", "No source:") + names(noSource).ifEmpty { listOf(NONE) } +
            listOf("", "No row in npc_combat.jsonc (Luna fights them with Man's stats):") +
            noRow.map { "  ${name(it.npc)}, from ${it.source.label}" }.ifEmpty { listOf(NONE) }
        return (header + changes + missing).joinToString(separator = "\n", postfix = "\n")
    }

    private fun changeLines(outcome: ImportOutcome.Updated): List<String> =
        listOf("", "${name(outcome.npc)}, from ${outcome.source.label}") +
            outcome.changes.map { "  ${it.field}: ${it.before} -> ${it.after}" } +
            outcome.notes.map { "  $it" }

    private fun names(outcomes: List<ImportOutcome>): List<String> = outcomes.map { "  ${name(it.npc)}" }

    private fun name(npc: NpcIdentity): String = "${npc.name} (${npc.id}, level ${npc.combatLevel})"

    private companion object {
        const val NONE = "  none"
    }
}
