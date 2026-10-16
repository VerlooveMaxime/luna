// IdleRS additions to the upstream build, applied from the last line of build.gradle.kts.

// Upstream's bot and skill tests fill JVM-wide state with fixtures: definitions in the global repositories, which take
// each id once and then lock, and a mocked context bound for api.predef, which reads it once. TestWorld fills the same
// state from the cache, so whichever comes first in a JVM breaks the other. Those tests, and every test that reaches
// their fixtures, run in a JVM of their own (fixtureTest, part of check: it takes about nine minutes, since many of
// them wait out real timed actions). TestContexts binds TestWorld's own context, so it is no fixture.
fun fixtureTestClasses(): List<String> {
    val sources = listOf("src/test/kotlin", "src/test/java").map(::file).flatMap { root ->
        fileTree(root) { include("**/*.kt", "**/*.java") }.files.map { it to it.relativeTo(root).invariantSeparatorsPath }
    }
    val texts = sources.associate { (source, _) -> source to source.readText() }
    val fillsGlobals = Regex("""\bstore(Definition|AndLock)\(|"setBindings"""")
    var fixtures = sources.filter { (source, _) ->
        source.name != "TestContexts.java" && fillsGlobals.containsMatchIn(texts.getValue(source))
    }.toSet()
    while (true) {
        val names = fixtures.map { (source, _) -> Regex("""\b${source.nameWithoutExtension}\b""") }
        val reached = fixtures + sources.filter { (source, _) -> names.any { it.containsMatchIn(texts.getValue(source)) } }
        if (reached.size == fixtures.size) break
        fixtures = reached
    }
    return fixtures.flatMap { (_, path) ->
        val className = path.substringBeforeLast('.')
        listOf("$className.class", "$className$*.class")
    }
}

val fixtureTests = fixtureTestClasses()

// Tests that read the cache decode the whole map, about 10 M tiles; the 512 MB default runs out of heap.
// LocationsDataTest reads data/idle and NpcCombatFileTest npc_combat.jsonc, so an edit there must rerun the tests
// instead of leaving them up to date.
tasks.named<Test>("test") {
    maxHeapSize = "2g"
    inputs.dir("data/idle")
    inputs.file("data/game/def/npcs/npc_combat.jsonc")
    exclude(fixtureTests)
}

tasks.register<Test>("fixtureTest") {
    description = "Runs the upstream tests that fill JVM-wide state with fixtures, in a JVM apart from TestWorld's."
    group = "verification"
    val testSources = project.the<SourceSetContainer>()["test"]
    testClassesDirs = testSources.output.classesDirs
    classpath = testSources.runtimeClasspath
    useJUnitPlatform()
    maxHeapSize = "2g"
    include(fixtureTests)
    onlyIf("some test fills JVM-wide state with fixtures") { fixtureTests.isNotEmpty() }
}

tasks.named("check") { dependsOn("fixtureTest") }

tasks.register<JavaExec>("treeSurvey") {
    group = "idlers"
    description = "Writes where each kind of tree grows, read from the cache, to .memory/artifacts/tree-survey/."
    classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass = "game.idle.location.survey.SurveyReportKt"
    workingDir = projectDir
    maxHeapSize = "2g"
    args(rootDir.resolve("../.memory/artifacts/tree-survey").normalize().path)
}

// The sources are whole checkouts in .memory/artifacts/sources/ (see .memory/tasks/content-tooling/importer-spike.md).
tasks.register<JavaExec>("importNpcCombat") {
    group = "idlers"
    description = "Imports npc combat rows from LostCity and OSRS for the regions or zones in -Pareas, " +
        "reporting to .memory/artifacts/combat-import/."
    classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass = "game.idle.content.combat.NpcCombatImporterKt"
    workingDir = projectDir
    maxHeapSize = "2g"
    val artifacts = rootDir.resolve("../.memory/artifacts").normalize()
    val areas = (findProperty("areas") as String?).orEmpty().split(" ").filter { it.isNotBlank() }
    args(listOf(artifacts.resolve("sources").path, "data", artifacts.resolve("combat-import/report.txt").path) + areas)
}

val idlersPackages = listOf("game/idle", "game/harness")

// Ratchet: the build fails when more lines or branches are missed than this. Lower it whenever tests close a gap.
val maxMissedLines = 0
val maxMissedBranches = 0

// Scripts only wire events to tested classes and cannot run without a booted server, so they are not counted.
fun idlersClasses(): FileTree {
    val scriptRoot = file("src/main/kotlin/game")
    val scriptClasses = fileTree(scriptRoot) { include(idlersPackages.map { "$it/**/*.kts" }) }.files.map { script ->
        val packagePath = script.parentFile.relativeTo(scriptRoot).invariantSeparatorsPath
        val className = script.name.removeSuffix(".kts").replaceFirstChar(Char::uppercaseChar).replace('.', '_')
        "$packagePath/$className"
    }
    return the<SourceSetContainer>()["main"].output.classesDirs.asFileTree.matching {
        include(idlersPackages.map { "$it/**/*.class" })
        exclude(scriptClasses.flatMap { listOf("$it.class", "$it$*.class") })
    }
}

// Line and branch coverage of our own packages; the tasks and the summary are shared with the client.
extra["idlersCoverageName"] = "server"
extra["idlersCoverageClasses"] = idlersClasses()
extra["maxMissedLines"] = maxMissedLines
extra["maxMissedBranches"] = maxMissedBranches
extra["idlersRatchetFile"] = "luna/idlers.gradle.kts"
apply(from = "idlers-coverage.gradle.kts")
