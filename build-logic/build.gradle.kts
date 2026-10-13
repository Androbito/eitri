plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.sqldelight.gradle.plugin)
}

kotlin {
    jvmToolchain(21)
}
