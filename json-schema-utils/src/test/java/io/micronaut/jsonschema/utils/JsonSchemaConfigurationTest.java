package io.micronaut.jsonschema.utils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class JsonSchemaConfigurationTest {

    @Test
    void testDefaultOutputLocation(JsonSchemaConfiguration jsonSchemaConfiguration) {
        assertEquals("schemas", jsonSchemaConfiguration.getOutputLocation());
    }
}
