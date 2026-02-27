package io.micronaut.jsonschema.configuration.validator.management;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = ConfigurationValidatorConfiguration.PREFIX + ".dependency-injection.enabled", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "field")
@MicronautTest
class InjectErrorsHealthIndicatorTest {
    @Test
    void healthIndicatorReportsDownWhenInjectErrorsPresent(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.retrieve("/health"));
        Optional<Map<String, Object>> response = ex.getResponse().getBody(Argument.mapOf(String.class, Object.class));
        Map<String, Object> decoded = response.get();
        assertEquals("DOWN", decoded.get("status"), () -> "Unexpected response: " + decoded);
    }
}
