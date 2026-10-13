plugins {
    id("servante.compose-library")
}

dependencies {
    api(projects.core)
    api(compose.desktop.currentOs)
    api(compose.foundation)
    api(compose.material3)

    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
