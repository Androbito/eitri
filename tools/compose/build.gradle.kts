// Scaffolded. The tool itself lands in its milestone; the module exists now so the dependency rule in
// docs/adr/0003 is declared from the start: a tool depends on core, never on ui, never on another tool.
plugins {
    id("servante.explicit-api")
}

dependencies {
    api(projects.core)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlinx.coroutines.test)
}
