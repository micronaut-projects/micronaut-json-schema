plugins {
    id("io.micronaut.build.internal.json-schema-module")
}
dependencies {
    api(mn.micronaut.json.core)
    api(projects.micronautJsonSchemaUtils)

    testAnnotationProcessor(mn.micronaut.inject.java)
    // Serialization
    testImplementation(mn.micronaut.jackson.databind)
    // The validator TCK shared with micronaut-json-schema-validator-networknt
    testImplementation(projects.testSuiteValidatorTck)
    testImplementation(mnTest.junit.platform.suite)
}
micronautBuild {
    testFramework = io.micronaut.build.TestFramework.JUNIT6
    // new in 2.3.2: no published baseline
    binaryCompatibility.enabledAfter("2.3.2")
}

micronautBuild {
    descriptor {
        parentModuleId = "io.micronaut.jsonschema:micronaut-json-schema-validator"
    }
}
