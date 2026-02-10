package io.micronaut.jsonschema.configuration.validator.management;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = "test.config.enabled", value = "not-a-bool")
@Property(name = "test.config.count", value = "1")
@MicronautTest
class ConfigurationHealthIndicatorTest {
    @Test
    void healthIndicatorReportsDownWhenErrorsPresent(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.retrieve("/health"));
        Optional<Map<String, Object>> response = ex.getResponse().getBody(Argument.mapOf(String.class, Object.class));
        Map<String, Object> decoded = response.get();
        assertEquals("DOWN", decoded.get("status"), () -> "Unexpected response: " + decoded);
    }
}
