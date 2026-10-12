// The rule engine the Accessibility and Security tools both sit on (M7).
//
// It is the one module several tools depend on, which docs/adr/0003 allows explicitly: it holds the
// engine and no tool-specific rules, so it is a shared module like core rather than a tool depending on
// a tool.
plugins {
    id("servante.explicit-api")
}

dependencies {
    api(projects.core)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.kotlinx.coroutines.test)
}
