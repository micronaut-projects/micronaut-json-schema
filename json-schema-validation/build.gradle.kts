plugins {
    id("io.micronaut.build.internal.json-schema-module")
}
dependencies {
    api(mn.micronaut.json.core)
    api(projects.micronautJsonSchemaUtils)
    api(libs.managed.json.schema.validator)

    // JSON Schema
    testAnnotationProcessor(projects.micronautJsonSchemaProcessor)

    // Validation
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)
    testImplementation(mnValidation.micronaut.validation)

    // Serialization
    testImplementation(mn.micronaut.jackson.databind)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(libs.junit.jupiter.params)
}
micronautBuild {
    testFramework = io.micronaut.build.TestFramework.JUNIT5
}
