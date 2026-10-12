import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The desktop shell: the window, navigation, and the composition root that wires every tool to its
// screen. The only module that knows how objects are built (docs/adr/0004).
plugins {
    id("servante.compose-library")
}

dependencies {
    implementation(projects.core)
    implementation(projects.ui)
    implementation(projects.storage)
    implementation(projects.tools.signing)
    implementation(libs.kotlinx.coroutines.swing)
    runtimeOnly(libs.slf4j.nop)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "app.servante.app.MainKt"

        nativeDistributions {
            // The formats the roadmap commits to. AppImage is assembled separately from the app image
            // this produces, because jpackage has no AppImage target: see scripts/packaging.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Servante"
            packageVersion = "0.1.0"
            description = "The open source toolbox for Android and Kotlin developers"
            vendor = "Androbito"
            licenseFile.set(rootProject.file("LICENSE"))

            // Nothing in Servante loads a class by name, so an aggressive runtime image is safe. These
            // are the modules the JDK pieces Servante uses actually need.
            modules("java.sql", "java.naming", "java.management", "jdk.security.auth")
        }
    }
}
