// The headless entry point. It shares the tools modules with the app and never depends on ui, so a
// check can run with no display.
//
// `servante doctor`, which runs every tool and grades its exit code by severity, is M9. What is here
// now is the Signing tool alone, so M1 can be checked against a real project before the screens exist.
plugins {
    id("servante.kotlin-library")
    application
}

dependencies {
    implementation(projects.core)
    implementation(projects.storage)
    implementation(projects.tools.signing)
    implementation(libs.kotlinx.coroutines.core)

    // storage brings SQLDelight, which asks SLF4J for a logger. Without a binding it prints a warning
    // on the first line of output, which would end up in a diagnosis someone pastes into an issue.
    runtimeOnly(libs.slf4j.nop)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    applicationName = "servante-signing"
    mainClass = "app.servante.cli.MainKt"
}
