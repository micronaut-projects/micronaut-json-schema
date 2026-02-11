package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "micronaut.server.ssl.enabled", value = "not-a-bool")
@MicronautTest
class InvalidConfigurationTest {
    @Test
    void configurationValidation(ConfigurationErrors configurationErrors) {
        Set<ConfigurationError> errors = configurationErrors.getCurrentErrors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.property().equals("micronaut.server.ssl.enabled") && e.message().contains("boolean")));
    }
}
