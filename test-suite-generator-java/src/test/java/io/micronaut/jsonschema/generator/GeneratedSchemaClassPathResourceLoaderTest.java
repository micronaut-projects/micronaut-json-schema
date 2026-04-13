package io.micronaut.jsonschema.generator;

import io.micronaut.jsonschema.generator.animals.Animal;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class GeneratedSchemaClassPathResourceLoaderTest {

    @Test
    void itLoadsEmbeddedSchemaForGeneratedType(JsonSchemaClassPathResourceLoader resourceLoader) {
        assertTrue(resourceLoader.jsonSchemaStringForClass(Animal.class).isPresent());
    }
}
