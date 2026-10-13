plugins {
    id("servante.sqldelight")
}

dependencies {
    api(projects.core)
    implementation(libs.tomlj)
    compileOnly(libs.checker.qual)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

sqldelight {
    databases {
        create("ServanteDatabase") {
            packageName.set("app.servante.storage.db")
            // Schema changes are verified against the committed schema rather than applied blindly:
            // the audit log is history the product promises to keep. See docs/adr/0005.
            verifyMigrations.set(true)
            schemaOutputDirectory.set(layout.projectDirectory.dir("src/main/sqldelight/databases").asFile)
        }
    }
}
