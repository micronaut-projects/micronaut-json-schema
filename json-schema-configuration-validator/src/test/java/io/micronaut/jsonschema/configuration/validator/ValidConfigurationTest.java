package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class ValidConfigurationTest {

    @Test
    void configurationValidation(EnvironmentConfigurationValidator validator) {
        Set<ConfigurationError> errors = validator.validate();
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }
}
