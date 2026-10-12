plugins {
    base
}

// detekt and ktlint run as their own self-contained command line tools rather than as Gradle
// plugins, so that neither is coupled to the Gradle or Kotlin version this build uses.
// See docs/adr/0006-run-detekt-and-ktlint-as-command-line-tools.md
val ktlint: Configuration by configurations.creating
val detekt: Configuration by configurations.creating

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:${libs.versions.ktlintCli.get()}:all@jar")
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detektCli.get()}:all@jar")
}

private val sourceGlobs = listOf(
    "**/src/**/*.kt",
    "**/src/**/*.kts",
    "*.kts",
    "build-logic/*.kts",
    // Generated Kotlin DSL accessors and extracted plugin blocks are not ours to format.
    "!**/build/**",
)

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin formatting with ktlint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = sourceGlobs + listOf("--relative", "--reporter=plain")
    // ktlint reads the whole tree; it is cheap enough that caching it is not worth the staleness risk.
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Rewrites Kotlin sources to match ktlint."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = sourceGlobs + listOf("--format", "--relative")
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Runs detekt static analysis."
    classpath = configurations["detekt"]
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args = listOf(
        "--input",
        listOf("core", "storage", "ui", "app", "cli", "ai", "tools").joinToString(","),
        "--config",
        layout.projectDirectory.file("config/detekt.yml").asFile.path,
        "--build-upon-default-config",
        "--report",
        "md:${layout.buildDirectory.get()}/reports/detekt/detekt.md",
    )
    outputs.upToDateWhen { false }
}

tasks.named("check") {
    dependsOn("ktlintCheck", "detekt")
}

// `./gradlew checkAll` is what CI runs and what a contributor runs before a pull request.
tasks.register("checkAll") {
    group = "verification"
    description = "Everything CI checks: tests, ktlint and detekt across every module."
    dependsOn(subprojects.map { "${it.path}:check" })
    dependsOn("ktlintCheck", "detekt")
}
