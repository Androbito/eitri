rootProject.name = "servante"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":core",
    ":storage",
    ":ui",
    ":app",
    ":cli",
    ":ai",
    ":tools:signing",
    ":tools:compose",
    ":tools:gradle",
    ":tools:sdk",
    ":tools:preflight",
    ":tools:releases",
    ":tools:reminders",
    ":tools:rules",
    ":tools:a11y",
    ":tools:security",
)
