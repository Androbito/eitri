plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    "testImplementation"(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Findings and dates are formatted; keep tests independent of the machine's locale and zone.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    // Tests read the sample projects and keystores in fixtures/ rather than building temporary ones,
    // so they exercise the same files a user's project has.
    systemProperty("servante.fixtures", rootProject.layout.projectDirectory.dir("fixtures").asFile.path)
}
