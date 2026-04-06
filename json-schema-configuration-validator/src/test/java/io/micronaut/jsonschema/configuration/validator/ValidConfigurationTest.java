package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class ValidConfigurationTest {

    @Test
    void configurationValidation(ConfigurationErrors configurationErrors) {
        Set<ConfigurationError> errors = configurationErrors.getCurrentErrors();
        assertNotNull(errors);
        assertFalse(errors.stream().anyMatch(e -> e.type() == ConfigurationError.Type.ERROR), () -> "Unexpected errors: " + errors);
        assertTrue(errors.stream().allMatch(e -> e.property() == null || e.property().startsWith("micronaut.")), () -> "Unexpected warnings: " + errors);
    }
}
