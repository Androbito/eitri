plugins {
    id("servante.serialization")
}

dependencies {
    api(projects.core)
    implementation(libs.kotlinx.serialization.json)

    // The end-to-end test detects the fixture project with the real detector rather than hand-building
    // an AndroidProject, so the tool is exercised against what a user's project actually parses to.
    testImplementation(projects.storage)
    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
