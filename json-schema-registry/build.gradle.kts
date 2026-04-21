plugins {
    id("io.micronaut.build.internal.json-schema-module")
}
dependencies {
    // Internal reuse
    implementation(projects.micronautJsonSchemaUtils)

    // Micronaut context/events, DI
    implementation(mn.micronaut.context)

    // JSON handling
    implementation(mn.jackson.databind)

    // Annotation processors for tests
    testAnnotationProcessor(projects.micronautJsonSchemaProcessor)
    testAnnotationProcessor(mn.micronaut.inject.java)

    // Tests
    testImplementation(mnTest.junit.jupiter.params)
}
micronautBuild {
    testFramework = io.micronaut.build.TestFramework.JUNIT5
}
