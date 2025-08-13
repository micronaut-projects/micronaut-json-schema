package io.micronaut.jsonschema.utils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class JsonSchemaClassPathResourceLoaderTest {

    @Test
    void itIsPossibleToGetTheJsonSchema(JsonSchemaClassPathResourceLoader resourceLoader) {
        assertTrue(resourceLoader.jsonSchemaStringForClass(Product.class).isPresent());
        assertFalse(resourceLoader.jsonSchemaStringForClass(Test.class).isPresent());
    }

}
