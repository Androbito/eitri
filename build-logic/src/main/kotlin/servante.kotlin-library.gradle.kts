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

// kotlinx-coroutines-test marks its dispatchers experimental, and the testing strategy says to pick one
// deliberately rather than avoid them. Opting in for test code only keeps main code honest about it.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("Test")) {
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
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
