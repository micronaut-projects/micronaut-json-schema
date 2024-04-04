package io.micronaut.jsonschema.test;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class SchemaGenerationTest {
    @Inject
    ResourceLoader resourceLoader;

    @ParameterizedTest
    @ValueSource(strings = {"llama", "bird", "possum", "possum-environment", "red-winged-blackbird", "salamander" })
    void buildJsonSchema(String name) {
        assertTrue(resourceLoader.getResource("META-INF/schemas/" + name + ".schema.json").isPresent());
    }
}
