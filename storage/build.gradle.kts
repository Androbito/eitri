plugins {
    id("servante.explicit-api")
}

dependencies {
    api(projects.core)
    implementation(libs.tomlj)
    compileOnly(libs.checker.qual)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}
