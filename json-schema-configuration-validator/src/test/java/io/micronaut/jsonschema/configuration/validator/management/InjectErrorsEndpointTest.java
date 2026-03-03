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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = ConfigurationValidatorConfiguration.PREFIX + ".dependency-injection.enabled", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "field")
@Property(name = "endpoints.injecterrors.sensitive", value = StringUtils.FALSE)
@MicronautTest
class InjectErrorsEndpointTest {

    @Test
    void injectErrorsEndpointExposesErrorsNamespace(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        Map<String, Object> decoded = assertDoesNotThrow(() -> client.retrieve(HttpRequest.GET("/injecterrors"), Argument.mapOf(String.class, Object.class)));
        assertTrue(decoded.containsKey("errors"), () -> "Unexpected response: " + decoded);
        Object errors = decoded.get("errors");
        assertInstanceOf(List.class, errors, () -> "Expected list, got: " + errors);
    }
}
