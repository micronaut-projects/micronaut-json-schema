plugins {
    id("io.micronaut.build.internal.json-schema-module")
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
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

    // Needed to load Micronaut's shipped configuration schemas in tests
    testImplementation(mn.micronaut.http)
    testImplementation(mn.micronaut.context)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation("io.micronaut:micronaut-http-client-core")
    testImplementation("io.micronaut:micronaut-jackson-databind")
    testImplementation("org.yaml:snakeyaml")
    testImplementation("io.micronaut.toml:micronaut-toml:2.7.0")

    // Needed for @ConfigurationProperties schema generation in tests
    testAnnotationProcessor(mn.micronaut.core.processor)
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)
    testImplementation(mnValidation.micronaut.validation)

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
