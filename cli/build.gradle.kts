// The headless entry point (M9). It shares the tools modules with the app and never depends on ui, so
// `servante doctor` can run in a pipeline with no display.
plugins {
    id("servante.kotlin-library")
}

dependencies {
    implementation(projects.core)
    implementation(projects.storage)
    implementation(projects.tools.signing)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlinx.coroutines.test)
}
