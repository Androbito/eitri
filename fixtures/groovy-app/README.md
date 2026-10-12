# Fixture: groovy-app

A second project with the Groovy DSL and no version catalog, so project detection is tested against both
DSLs and against a project that has none of the optional files. Its keystore passwords come from the
environment, which is the shape the secret scanner must *not* flag.
