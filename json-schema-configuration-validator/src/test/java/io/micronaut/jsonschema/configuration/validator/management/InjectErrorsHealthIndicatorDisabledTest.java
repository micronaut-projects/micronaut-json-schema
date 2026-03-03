package io.micronaut.jsonschema.configuration.validator.management;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = ConfigurationValidatorConfiguration.PREFIX + ".dependency-injection.enabled", value = StringUtils.TRUE)
@Property(name = ConfigurationValidatorConfiguration.ENDPOINT_PREFIX + ".enabled", value = StringUtils.FALSE)
@Property(name = "spec.name", value = "field")
@MicronautTest
class InjectErrorsHealthIndicatorDisabledTest {
    @Test
    void healthIndicatorCanBeDisabled(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        Map<String, Object> decoded = assertDoesNotThrow(() -> client.retrieve(HttpRequest.GET("/health/liveness"), Argument.mapOf(String.class, Object.class)));
        assertEquals("UP", decoded.get("status"), () -> "Unexpected response: " + decoded);
    }
}
