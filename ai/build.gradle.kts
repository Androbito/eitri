// AI providers (M8). Bring your own key, nothing sent without the user seeing the payload.
//
// Depends on core only, like a tool: the payload builder works on files and findings, and the secret
// exclusion goes through the same scanner the Security tool uses.
//
// No HTTP client yet, on purpose. Declaring Ktor here before there is any code to use it costs every
// build the resolution of its transitive graph, which for ktor-serialization-kotlinx-json reaches as
// far as a YAML parser, and buys nothing. It arrives with the first provider client.
plugins {
    id("servante.serialization")
}

dependencies {
    api(projects.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlinx.coroutines.test)
}
