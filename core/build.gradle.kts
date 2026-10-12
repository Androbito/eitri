plugins {
    id("servante.explicit-api")
    // Fakes live here and are consumed by the other modules with testFixtures(projects.core), which is
    // the shared test module the testing strategy asks for without adding a module to the map.
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
