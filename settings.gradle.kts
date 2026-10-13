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
        // Central first, deliberately: everything but Compose resolves from it, so a build in an
        // environment that cannot reach Google's repository still gets as far as the UI modules.
        mavenCentral()
        // Compose Multiplatform depends on androidx, which is published here and nowhere else.
        // This is also one of the hosts Servante itself contacts, for dependency versions in the
        // Gradle tool: see NetworkDestinations in core.
        google()
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
