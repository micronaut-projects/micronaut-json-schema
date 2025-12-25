plugins {
    id("io.micronaut.build.internal.json-schema-module")
}
dependencies {
    api(projects.micronautJsonSchemaAnnotations)
    testAnnotationProcessor(projects.micronautJsonSchemaProcessor)
    testAnnotationProcessor(mn.micronaut.inject.java)
}
micronautBuild {
    binaryCompatibility.enabledAfter("1.7.0")
//    testFramework = io.micronaut.build.TestFramework.JUNIT5
}
