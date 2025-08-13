package io.micronaut.jsonschema.utils;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "micronaut.jsonschema.output-location", value = "jsonschemas")
@MicronautTest(startApplication = false)
class JsonSchemaConfigurationPropertiesTest {
    @Test
    void testOutputLocationChangedViaProperty(JsonSchemaConfiguration jsonSchemaConfiguration) {
        assertEquals("jsonschemas", jsonSchemaConfiguration.getOutputLocation());
    }
}
