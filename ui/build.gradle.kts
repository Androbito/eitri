plugins {
    id("servante.compose-library")
}

dependencies {
    api(projects.core)
    api(compose.desktop.currentOs)
    api(compose.foundation)
    api(compose.material3)
    implementation(compose.components.resources)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
