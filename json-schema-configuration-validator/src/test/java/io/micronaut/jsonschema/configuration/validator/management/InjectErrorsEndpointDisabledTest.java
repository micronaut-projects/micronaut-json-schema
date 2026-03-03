package io.micronaut.jsonschema.configuration.validator.management;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.jsonschema.configuration.validator.ConfigurationValidatorConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = ConfigurationValidatorConfiguration.PREFIX + ".dependency-injection.enabled", value = StringUtils.TRUE)
@Property(name = ConfigurationValidatorConfiguration.PREFIX + ".injecterrors.endpoint.enabled", value = StringUtils.FALSE)
@Property(name = "endpoints.injecterrors.enabled", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "field")
@MicronautTest
class InjectErrorsEndpointDisabledTest {

    @Test
    void injectErrorsEndpointCanBeDisabled(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.GET("/injecterrors")));
    }
}
