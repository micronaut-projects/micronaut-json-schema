plugins {
    id("io.micronaut.build.internal.json-schema-module")
}
dependencies {
    api(mn.micronaut.json.core)
    api(projects.micronautJsonSchemaUtils)

    // JSON Schema
    testAnnotationProcessor(projects.micronautJsonSchemaProcessor)

    // Validation
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)
    testImplementation(mnValidation.micronaut.validation)

    // Serialization
    testImplementation(mn.micronaut.jackson.databind)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.junit.jupiter.params)
}
micronautBuild {
    testFramework = io.micronaut.build.TestFramework.JUNIT6
    // 2.3.2 removes the internal NetworkNT based JsonSchemaValidationFactory (and its generated bean definitions)
    // and changes the constructor of the internal ValidationMessageAdapter; the public API is unchanged
    binaryCompatibility.enabledAfter("2.3.2")
}

micronautBuild {
    descriptor {
        parentModuleId = "io.micronaut.jsonschema:micronaut-json-schema-validation"
    }
}
