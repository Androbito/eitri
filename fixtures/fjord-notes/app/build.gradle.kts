plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.fjord.notes"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.fjord.notes"
        minSdk = 24
        targetSdk = 35
        versionCode = 412
        versionName = "3.8.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystores/release.jks")
            storePassword = "fjord-release-2024"
            keyAlias = "upload"
            keyPassword = "fjord-release-2024"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("direct") { dimension = "store" }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
}
