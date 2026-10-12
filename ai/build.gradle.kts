// AI providers (M8). Bring your own key, nothing sent without the user seeing the payload.
//
// Depends on core only, like a tool: the payload builder works on files and findings, and the secret
// exclusion goes through the same scanner the Security tool uses.
plugins {
    id("servante.serialization")
}

dependencies {
    api(projects.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
