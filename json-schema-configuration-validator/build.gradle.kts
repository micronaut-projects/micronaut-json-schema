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

    compileOnly(mn.micronaut.management)

    annotationProcessor(mnSerde.micronaut.serde.processor)
    annotationProcessor(mn.micronaut.inject.java)

    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testAnnotationProcessor(mn.micronaut.inject.java)

    // Needed to load Micronaut's shipped configuration schemas in tests
    testImplementation(mn.micronaut.http)
    testImplementation(mn.micronaut.context)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.management)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.jackson.databind)
    testImplementation(libs.micronaut.sql.jdbc.hikari)
    testImplementation(mn.snakeyaml)
    testImplementation(libs.micronaut.toml)

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
