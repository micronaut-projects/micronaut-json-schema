plugins {
    id("io.micronaut.build.internal.json-schema-module")
}

dependencies {
    api(projects.micronautJsonSchemaUtils)
    api(mn.micronaut.inject)
    api(mn.micronaut.json.core)

    implementation(mnSerde.micronaut.serde.jackson)

    annotationProcessor(mnSerde.micronaut.serde.processor)
    annotationProcessor(mn.micronaut.inject.java)

    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testAnnotationProcessor(mn.micronaut.inject.java)

    testImplementation(mnTest.junit.jupiter.params)
}

micronautBuild {
    testFramework = io.micronaut.build.TestFramework.JUNIT6
}

micronautBuild {
    descriptor {
        parentModuleId = "io.micronaut.jsonschema:micronaut-json-schema-configuration-validator"
    }
}
