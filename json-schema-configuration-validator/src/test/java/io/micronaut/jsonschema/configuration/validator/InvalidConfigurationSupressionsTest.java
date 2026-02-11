package io.micronaut.jsonschema.configuration.validator;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Property(name = "micronaut.jsonschema.configuration.validator.suppressions[0]", value = "micronaut.server.ssl.*")
@Property(name = "micronaut.server.ssl.enabled", value = "not-a-bool")
@MicronautTest
class InvalidConfigurationSupressionsTest {
    @Test
    void configurationValidation(ConfigurationErrors configurationErrors) {
        Set<ConfigurationError> errors = configurationErrors.getCurrentErrors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

        ConfigurationError sslEnabled = errors.stream()
            .filter(e -> "micronaut.server.ssl.enabled".equals(e.property()))
            .findFirst()
            .orElse(null);
        assertNotNull(sslEnabled, () -> "Expected validation error for 'micronaut.server.ssl.enabled', got: " + errors);
        assertEquals(ConfigurationError.Type.WARNING, sslEnabled.type(), () -> "Expected ssl.enabled to be suppressed, got: " + errors);

        ConfigurationError unsuppressedSslError = errors.stream()
            .filter(e -> e.type() == ConfigurationError.Type.ERROR)
            .filter(e -> e.property() != null)
            .filter(e -> e.property().startsWith("micronaut.server.ssl."))
            .findFirst()
            .orElse(null);
        assertNull(unsuppressedSslError, () -> "Expected no ERRORs under micronaut.server.ssl.*, got: " + errors);

        assertFalse(errors.stream().anyMatch(e -> e.type() == ConfigurationError.Type.ERROR),
            () -> "Expected no unsuppressed ERRORs, got: " + errors);
    }
}
