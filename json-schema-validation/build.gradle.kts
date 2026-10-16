plugins {
    id("io.micronaut.build.internal.json-schema-module")
}
dependencies {
    // micronaut-json-schema-validation keeps validating with NetworkNT, as before 2.3.2. Depend on
    // micronaut-json-schema-validator instead to use the built-in validator without NetworkNT.
    api(projects.micronautJsonSchemaValidator)
    api(projects.micronautJsonSchemaValidatorNetworknt)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.jackson.databind)
}
micronautBuild {
    testFramework = io.micronaut.build.TestFramework.JUNIT6
    // 2.3.2 moves the classes of this module to micronaut-json-schema-validator and
    // micronaut-json-schema-validator-networknt, which it depends on
    binaryCompatibility.enabledAfter("2.3.2")
}

micronautBuild {
    descriptor {
        parentModuleId = "io.micronaut.jsonschema:micronaut-json-schema-validation"
    }
}
