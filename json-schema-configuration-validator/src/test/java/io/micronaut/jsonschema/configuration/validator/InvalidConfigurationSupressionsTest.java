package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "micronaut.jsonschema.configuration.validator.suppressions[0]", value = "micronaut.server.ssl.*")
@Property(name = "micronaut.server.ssl.enabled", value = "not-a-bool")
@MicronautTest
class InvalidConfigurationSupressionsTest {
    @Test
    void configurationValidation(ConfigurationErrors configurationErrors) {
        Set<ConfigurationError> errors = configurationErrors.getCurrentErrors();
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }
}
