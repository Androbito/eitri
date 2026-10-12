// Applied on top of servante.kotlin-library for modules that are an API for other modules.
plugins {
    id("servante.kotlin-library")
}

kotlin {
    explicitApi()
}
